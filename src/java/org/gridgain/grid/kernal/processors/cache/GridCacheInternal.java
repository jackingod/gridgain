// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

/**
 * Marker interface using in cache implementations.
 * If values and keys implements this interface, they will be excluded from some cache internal
 * operations (eviction, iteration).
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.1.0c.30052011
 */
public interface GridCacheInternal {
    // No-op.
}