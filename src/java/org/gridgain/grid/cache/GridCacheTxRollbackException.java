// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache;

import org.gridgain.grid.*;

/**
 * Exception thrown whenever grid transactions has been automatically rolled back.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.09012012
 */
public class GridCacheTxRollbackException extends GridException {
    /**
     * Creates new rollback exception with given error message.
     *
     * @param msg Error message.
     */
    public GridCacheTxRollbackException(String msg) {
        super(msg);
    }

    /**
     * Creates new rollback exception with given error message and optional nested exception.
     *
     * @param msg Error message.
     * @param cause Optional nested exception (can be <tt>null</tt>).
     */
    public GridCacheTxRollbackException(String msg, Throwable cause) {
        super(msg, cause);
    }
}