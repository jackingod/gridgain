// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.near;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.cache.GridCacheTxState.*;

/**
 * Replicated user transaction.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.09012012
 */
class GridNearTxLocal<K, V> extends GridCacheTxLocalAdapter<K, V> {
    /** Future. */
    private final AtomicReference<GridNearTxPrepareFuture<K, V>> prepFut =
        new AtomicReference<GridNearTxPrepareFuture<K, V>>();

    /** */
    private final AtomicReference<GridNearTxFinishFuture<K, V>> commitFut =
        new AtomicReference<GridNearTxFinishFuture<K, V>>();

    /** */
    private final AtomicReference<GridNearTxFinishFuture<K, V>> rollbackFut =
        new AtomicReference<GridNearTxFinishFuture<K, V>>();

    /** */
    private boolean syncCommit;

    /** */
    private boolean syncRollback;

    /** DHT mappings. */
    private ConcurrentMap<UUID, GridDistributedTxMapping<K, V>> mappings =
        new ConcurrentHashMap<UUID, GridDistributedTxMapping<K, V>>(16, 0.75f, 1);

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridNearTxLocal() {
        // No-op.
    }

    /**
     * @param ctx   Cache registry.
     * @param implicit Implicit flag.
     * @param implicitSingle Implicit with one key flag.
     * @param concurrency Concurrency.
     * @param isolation Isolation.
     * @param timeout Timeout.
     * @param invalidate Invalidation policy.
     * @param syncCommit Synchronous commit flag.
     * @param syncRollback Synchronous rollback flag.
     * @param swapEnabled Whether to use swap storage.
     * @param storeEnabled Whether to use read/write through.
     */
    GridNearTxLocal(
        GridCacheContext<K, V> ctx,
        boolean implicit,
        boolean implicitSingle,
        GridCacheTxConcurrency concurrency,
        GridCacheTxIsolation isolation,
        long timeout,
        boolean invalidate,
        boolean syncCommit,
        boolean syncRollback,
        boolean swapEnabled,
        boolean storeEnabled) {
        super(ctx, ctx.versions().next(), implicit, implicitSingle, concurrency, isolation, timeout, invalidate,
            swapEnabled, storeEnabled);

        assert ctx != null;

        this.syncCommit = syncCommit;
        this.syncRollback = syncRollback;
    }

    /** {@inheritDoc} */
    @Override public boolean near() {
        return true;
    }

    /** {@inheritDoc} */
    @Override public boolean enforceSerializable() {
        return false;
    }

    /** {@inheritDoc} */
    @Override public Collection<UUID> nodeIds() {
        Collection<UUID> ids = new GridLeanSet<UUID>();

        ids.add(cctx.nodeId());
        ids.addAll(mappings.keySet());

        return ids;
    }

    /**
     * @return DHT map.
     */
    ConcurrentMap<UUID, GridDistributedTxMapping<K, V>> mappings() {
        return mappings;
    }

    /**
     * @param key Key.
     * @return Mapping for the key.
     */
    @Nullable UUID mapping(K key) {
        GridCacheTxEntry<K, V> txEntry = txMap.get(key);

        return txEntry == null ? null : txEntry.nodeId();
    }

    /**
     * @param nodeId Node ID.
     * @param dhtVer DHT version.
     */
    void addDhtVersion(UUID nodeId, GridCacheVersion dhtVer) {
        // This step is very important as near and DHT versions grow separately.
        cctx.versions().onReceived(nodeId, dhtVer);

        GridDistributedTxMapping<K, V> m = mappings.get(nodeId);

        if (m != null)
            m.dhtVersion(dhtVer);
    }

    /**
     * @param nodeId Undo mapping.
     */
    public void removeMapping(UUID nodeId) {
        if (mappings.remove(nodeId) != null) {
            if (log.isDebugEnabled())
                log.debug("Removed mapping for node [nodeId=" + nodeId + ", tx=" + this + ']');
        }
        else if (log.isDebugEnabled())
            log.debug("Mapping for node was not found [nodeId=" + nodeId + ", tx=" + this + ']');
    }

    /**
     * @param nodeId Node ID.
     * @param key Key.
     */
    public void removeMapping(UUID nodeId, K key) {
        GridDistributedTxMapping<K, V> m = mappings.get(nodeId);

        if (m != null) {
            GridCacheTxEntry<K, V> txEntry = txMap.get(key);

            if (txEntry != null)
                m.removeEntry(txEntry);

            if (m.empty())
                mappings.remove(nodeId);
        }
    }

    /**
     * @param keyMap Key map to register.
     */
    void addKeyMapping(Map<GridRichNode, Collection<K>> keyMap) {
        for (Map.Entry<GridRichNode, Collection<K>> mapping : keyMap.entrySet()) {
            GridRichNode n = mapping.getKey();

            for (K key : mapping.getValue()) {
                GridCacheTxEntry<K, V> txEntry = txMap.get(key);

                assert txEntry != null;

                GridDistributedTxMapping<K, V> m = mappings.get(n.id());

                if (m == null)
                    mappings.put(n.id(), m = new GridDistributedTxMapping<K, V>(n));

                txEntry.nodeId(n.id());

                m.add(txEntry);
            }
        }

        if (log.isDebugEnabled())
            log.debug("Added mappings to transaction [locId=" + cctx.nodeId() + ", mappings=" + keyMap +
                ", tx=" + this + ']');
    }

    /**
     * @param mappings Mappings.
     */
    void addEntryMapping(@Nullable Map<UUID, GridDistributedTxMapping<K, V>> mappings) {
        if (!F.isEmpty(mappings)) {
            this.mappings.putAll(mappings);

            if (log.isDebugEnabled())
                log.debug("Added mappings to transaction [locId=" + cctx.nodeId() + ", mappings=" + mappings +
                    ", tx=" + this + ']');
        }
    }

    /**
     * @param nodeId Node ID to mark with explicit lock.
     * @return {@code True} if mapping was found.
     */
    boolean markExplicit(UUID nodeId) {
        GridDistributedTxMapping<K, V> m = mappings.get(nodeId);

        if (m != null) {
            m.markExplicitLock();

            return true;
        }

        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean syncCommit() {
        return syncCommit;
    }

    /** {@inheritDoc} */
    @Override public boolean syncRollback() {
        return syncRollback;
    }

    /** {@inheritDoc} */
    @Override public boolean onOwnerChanged(GridCacheEntryEx<K, V> entry, GridCacheMvccCandidate<K> owner) {
        GridNearTxPrepareFuture<K, V> fut = prepFut.get();

        return fut != null && fut.onOwnerChanged(entry, owner);
    }

    /**
     * @return Commit fut.
     */
    @Override public GridFuture<GridCacheTxEx<K, V>> future() {
        return prepFut.get();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> loadMissing(boolean async, final Collection<? extends K> keys,
        final GridInClosure2<K, V> closure) {
        GridFuture<Map<K, V>> f = cctx.near().txLoadAsync(this, keys, CU.<K, V>empty());

        return new GridEmbeddedFuture<Boolean, Map<K, V>>(
            cctx.kernalContext(),
            f,
            new C2<Map<K, V>, Exception, Boolean>() {
                @Override public Boolean apply(Map<K, V> map, Exception e) {
                    if (e != null) {
                        setRollbackOnly();

                        throw new GridClosureException(e);
                    }

                    // Must loop through keys, not map entries,
                    // as map entries may not have all the keys.
                    for (K key : keys)
                        closure.apply(key, map.get(key));

                    return true;
                }
            }
        );
    }

    /**
     * @param mapping Mapping to order.
     * @param committedVers Committed versions.
     * @param rolledbackVers Rolled back versions.
     */
    void orderCompleted(GridDistributedTxMapping<K, V> mapping, Collection<GridCacheVersion> committedVers,
        Collection<GridCacheVersion> rolledbackVers) {
        for (GridCacheTxEntry<K, V> txEntry : F.concat(false, mapping.reads(), mapping.writes())) {
            while (true) {
                GridDistributedCacheEntry<K, V> entry = (GridDistributedCacheEntry<K, V>)txEntry.cached();

                try {
                    // Handle explicit locks.
                    GridCacheVersion base = txEntry.explicitVersion() != null ? txEntry.explicitVersion() : xidVer;

                    entry.doneRemote(xidVer, base, committedVers, rolledbackVers);

                    if (ec())
                        entry.recheck();

                    break;
                }
                catch (GridCacheEntryRemovedException ignored) {
                    assert entry.obsoleteVersion() != null;

                    if (log.isDebugEnabled())
                        log.debug("Replacing obsolete entry in remote transaction [entry=" + entry +
                            ", tx=" + this + ']');

                    // Replace the entry.
                    txEntry.cached(cctx.cache().entryEx(txEntry.key()), entry.keyBytes());
                }
            }
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    @Override public boolean finishEC(boolean commit) throws GridException {
        GridException err = null;

        try {
            if (commit)
                state(COMMITTING);
            else
                state(ROLLING_BACK);

            if (commit && !isRollbackOnly()) {
                if (!userCommitEC())
                    return false;
            }
            else
                userRollback();
        }
        catch (GridException e) {
            err = e;

            commit = false;

            // If heuristic error.
            if (!isRollbackOnly())
                invalidate(true);
        }

        if (err != null) {
            state(UNKNOWN);

            throw err;
        }
        else {
            if (!state(commit ? COMMITTED : ROLLED_BACK)) {
                state(UNKNOWN);

                throw new GridException("Invalid transaction state for commit or rollback: " + this);
            }
        }

        return true;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"CatchGenericClass", "ThrowableInstanceNeverThrown"})
    @Override public void finish(boolean commit) throws GridException {
        if (log.isDebugEnabled())
            log.debug("Finishing near local tx [tx=" + this + ", commit=" + commit + "]");

        if (commit) {
            if (!state(COMMITTING)) {
                GridCacheTxState state = state();

                if (state != COMMITTING && state != COMMITTED)
                    throw new GridException("Invalid transaction state for commit [state=" + state() +
                        ", tx=" + this + ']');
                else {
                    if (log.isDebugEnabled())
                        log.debug("Invalid transaction state for commit (another thread is committing): " + this);

                    return;
                }
            }
        }
        else {
            if (!state(ROLLING_BACK)) {
                if (log.isDebugEnabled())
                    log.debug("Invalid transaction state for rollback [state=" + state() + ", tx=" + this + ']');

                return;
            }
        }

        GridException err = null;

        // Commit to DB first. This way if there is a failure, transaction
        // won't be committed.
        try {
            if (commit && !isRollbackOnly())
                userCommit();
            else
                userRollback();
        }
        catch (GridException e) {
            err = e;

            commit = false;

            // If heuristic error.
            if (!isRollbackOnly()) {
                invalidate = true;

                U.warn(log, "Set transaction invalidation flag to true due to error [tx=" + this + ", err=" + err + ']');
            }
        }

        if (err != null) {
            state(UNKNOWN);

            throw err;
        }
        else {
            if (!state(commit ? COMMITTED : ROLLED_BACK)) {
                state(UNKNOWN);

                throw new GridException("Invalid transaction state for commit or rollback: " + this);
            }
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<GridCacheTxEx<K, V>> prepareAsync() {
        GridNearTxPrepareFuture<K, V> fut = prepFut.get();

        if (fut == null) {
            // Future must be created before any exception can be thrown.
            if (!prepFut.compareAndSet(null, fut = new GridNearTxPrepareFuture<K, V>(cctx, this)))
                return prepFut.get();
        }
        else
            // Prepare was called explicitly.
            return fut;

        if (!state(PREPARING)) {
            if (setRollbackOnly()) {
                if (timedOut())
                    fut.onError(new GridCacheTxTimeoutException("Transaction timed out and was rolled back: " + this));
                else
                    fut.onError(new GridException("Invalid transaction state for prepare [state=" + state() +
                        ", tx=" + this + ']'));
            }

            return fut;
        }

        // For pessimistic mode we don't distribute prepare request.
        if (pessimistic()) {
            try {
                userPrepare();

                if (!state(PREPARED)) {
                    setRollbackOnly();

                    fut.onError(new GridException("Invalid transaction state for commit [state=" + state() +
                        ", tx=" + this + ']'));

                    return fut;
                }

                fut.complete();

                return fut;
            }
            catch (GridException e) {
                fut.onError(e);

                return fut;
            }
        }

        try {
            cctx.topology().readLock();

            try {
                topologyVersion(cctx.topology().topologyVersion());

                userPrepare();
            }
            finally {
                cctx.topology().readUnlock();
            }

            // This will attempt to locally commit
            // EVENTUALLY CONSISTENT transactions.
            fut.onPreparedEC();

            // Make sure to add future before calling prepare.
            cctx.mvcc().addFuture(fut);

            fut.prepare();
        }
        catch (GridCacheTxTimeoutException e) {
            fut.onError(e);
        }
        catch (GridCacheTxOptimisticException e) {
            fut.onError(e);
        }
        catch (GridException e) {
            setRollbackOnly();

            String msg = "Failed to prepare transaction (will attempt rollback): " + this;

            log.error(msg, e);

            try {
                rollback();
            }
            catch (GridException e1) {
                U.error(log, "Failed to rollback transaction: " + this, e1);
            }

            fut.onError(new GridCacheTxRollbackException(msg, e));
        }

        return fut;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    @Override public GridFuture<GridCacheTx> commitAsync() {
        if (log.isDebugEnabled())
            log.debug("Committing near local tx: " + this);

        prepareAsync();

        GridNearTxFinishFuture<K, V> fut = commitFut.get();

        if (fut == null && !commitFut.compareAndSet(null, fut = new GridNearTxFinishFuture<K, V>(cctx, this, true)))
            return commitFut.get();

        cctx.mvcc().addFuture(fut);

        prepFut.get().listenAsync(new CI1<GridFuture<GridCacheTxEx<K, V>>>() {
            @Override public void apply(GridFuture<GridCacheTxEx<K, V>> f) {
                try {
                    // Make sure that here are no exceptions.
                    f.get();

                    finish(true);

                    commitFut.get().finish();
                }
                catch (Error e) {
                    commitErr.compareAndSet(null, e);

                    throw e;
                }
                catch (RuntimeException e) {
                    commitErr.compareAndSet(null, e);

                    throw e;
                }
                catch (GridException e) {
                    commitErr.compareAndSet(null, e);

                    commitFut.get().onError(e);
                }
            }
        });

        return fut;
    }

    /** {@inheritDoc} */
    @Override public void rollback() throws GridException {
        GridNearTxPrepareFuture<K, V> prepFut = this.prepFut.get();

        GridNearTxFinishFuture<K, V> fut = rollbackFut.get();

        if (fut == null && !rollbackFut.compareAndSet(null, fut = new GridNearTxFinishFuture<K, V>(cctx, this, false))) {
            rollbackFut.get();

            return;
        }

        try {
            cctx.mvcc().addFuture(fut);

            if (prepFut == null) {
                finish(false);

                fut.finish();
            }
            else {
                prepFut.listenAsync(new CI1<GridFuture<GridCacheTxEx<K, V>>>() {
                    @Override public void apply(GridFuture<GridCacheTxEx<K, V>> f) {
                        try {
                            // Check for errors in prepare future.
                            f.get();
                        }
                        catch (GridException e) {
                            if (log.isDebugEnabled())
                                log.debug("Got optimistic tx failure [tx=" + this + ", err=" + e + ']');
                        }

                        try {
                            finish(false);

                            rollbackFut.get().finish();
                        }
                        catch (GridException e) {
                            U.error(log, "Failed to gracefully rollback transaction: " + this, e);

                            rollbackFut.get().onError(e);
                        }
                    }
                });
            }

            // TODO: Rollback Async?
            fut.get();
        }
        catch (Error e) {
            U.addLastCause(e, commitErr.get());

            throw e;
        }
        catch (RuntimeException e) {
            U.addLastCause(e, commitErr.get());

            throw e;
        }
        catch (GridException e) {
            U.addLastCause(e, commitErr.get());

            throw e;
        }
        finally {
            cctx.tm().txContextReset();
            cctx.near().dht().context().tm().txContextReset();
        }
    }

    /** {@inheritDoc} */
    @Override public void addLocalCandidates(K key, Collection<GridCacheMvccCandidate<K>> cands) {
        /* No-op. */
    }

    /** {@inheritDoc} */
    @Override public Map<K, Collection<GridCacheMvccCandidate<K>>> localCandidates() {
        return Collections.emptyMap();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridNearTxLocal.class, this, "mappings", mappings.keySet(), "super", super.toString());
    }
}
