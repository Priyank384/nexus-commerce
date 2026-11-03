package com.example.nexusCommerce.cache;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * Adds {@link EpochGuardedCache} to every cache a delegate manager hands out. Wrapping here
 * rather than inside the manager keeps the guard outside Spring's transaction decorator, which
 * is what lets the epoch move at the same instant as the eviction it reports.
 */
public final class EpochGuardedCacheManager implements CacheManager {

    private final CacheManager delegate;
    private final CacheEpochs epochs;
    private final Map<String, Cache> guarded = new ConcurrentHashMap<>();

    public EpochGuardedCacheManager(CacheManager delegate, CacheEpochs epochs) {
        this.delegate = delegate;
        this.epochs = epochs;
    }

    @Override
    public Cache getCache(String name) {
        Cache cache = delegate.getCache(name);
        if (cache == null) {
            return null;
        }
        return guarded.computeIfAbsent(name, key -> new EpochGuardedCache(cache, epochs));
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegate.getCacheNames();
    }
}
