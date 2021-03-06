// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache.eviction.always;

import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.eviction.*;

/**
 * Cache eviction policy that expires every entry essentially keeping the cache empty.
 * This eviction policy can be used whenever one cache is used to front another
 * and its size should be kept at {@code 0}.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.09012012
 */
public class GridCacheAlwaysEvictionPolicy<K, V> implements GridCacheEvictionPolicy<K, V>,
    GridCacheAlwaysEvictionPolicyMBean {
    /** {@inheritDoc} */
    @Override public void onEntryAccessed(boolean rmv, GridCacheEntry<K, V> entry) {
        if (!rmv)
            entry.evict();
    }
}
