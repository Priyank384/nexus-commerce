package com.example.nexusCommerce.cache;

import java.util.concurrent.Callable;

import org.springframework.cache.Cache;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Read-through caching leaves a window that evicting after commit cannot close by itself: a
 * reader that missed the cache <em>before</em> a write commits can finish its database load
 * <em>after</em> the eviction and write pre-commit state back into Redis, where it then survives
 * the whole TTL — which is why an admin edit can disappear from the tree for fifteen minutes.
 *
 * <p>Each invalidation bumps a per-cache epoch when it actually takes effect. A value is only
 * stored if the epoch has not moved between the moment this thread observed the miss and the
 * moment it puts; a value that cannot be shown to describe what its reader queried is dropped
 * instead of cached. The read itself always returns what the database said, so this only ever
 * costs a cache entry, never a wrong answer.
 */
public final class EpochGuardedCache implements Cache {

    /** The miss this thread is between: {@code get} records it, {@code put} consumes it. */
    private record Miss(String cacheName, Object key, long epoch) {
    }

    private static final ThreadLocal<Miss> PENDING_MISS = new ThreadLocal<>();

    private final Cache delegate;
    private final CacheEpochs epochs;

    public EpochGuardedCache(Cache delegate, CacheEpochs epochs) {
        this.delegate = delegate;
        this.epochs = epochs;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Object getNativeCache() {
        return delegate.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        ValueWrapper found = delegate.get(key);
        if (found == null) {
            PENDING_MISS.set(new Miss(getName(), key, epochs.current(getName())));
        } else {
            PENDING_MISS.remove();
        }
        return found;
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        return delegate.get(key, type);
    }

    /**
     * The {@code sync = true} path loads and stores inside the delegate, so the epoch is checked
     * around the whole call and an entry written from pre-commit state is removed again. The
     * epoch is only read once the loader actually runs: a cache hit costs nothing extra.
     */
    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        long[] epochWhenLoadStarted = {CacheEpochs.UNKNOWN};
        Callable<T> timed = () -> {
            epochWhenLoadStarted[0] = epochs.current(getName());
            return valueLoader.call();
        };

        T value = delegate.get(key, timed);
        if (epochWhenLoadStarted[0] != CacheEpochs.UNKNOWN
                && epochs.current(getName()) != epochWhenLoadStarted[0]) {
            delegate.evict(key);
        }
        return value;
    }

    @Override
    public void put(Object key, Object value) {
        Miss miss = PENDING_MISS.get();
        PENDING_MISS.remove();
        if (miss != null && miss.cacheName().equals(getName()) && miss.key().equals(key)) {
            long now = epochs.current(getName());
            if (miss.epoch() != CacheEpochs.UNKNOWN && now != CacheEpochs.UNKNOWN && now != miss.epoch()) {
                return;
            }
        }
        delegate.put(key, value);
    }

    @Override
    public void evict(Object key) {
        bumpAtCommit();
        delegate.evict(key);
    }

    @Override
    public void clear() {
        bumpAtCommit();
        delegate.clear();
    }

    /**
     * The delegate defers its own eviction to the commit, so the counter has to move at the same
     * instant: bumping when the write merely returns would let a reader that started after the
     * bump still load uncommitted state and consider its entry trustworthy.
     */
    private void bumpAtCommit() {
        String cacheName = getName();
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            epochs.bump(cacheName);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                epochs.bump(cacheName);
            }
        });
    }
}
