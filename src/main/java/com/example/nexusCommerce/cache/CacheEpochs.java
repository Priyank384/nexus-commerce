package com.example.nexusCommerce.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A per-cache invalidation counter. Readers compare it before and after a database load; a
 * writer bumps it when it evicts. Two epochs that differ mean the cached value cannot be
 * trusted to describe the state the reader actually queried.
 *
 * <p>Kept outside {@link EpochGuardedCache} so the counter can live in Redis in a deployment
 * with several application instances sharing one cache, and in memory for the simple cache
 * manager used by tests.
 */
public interface CacheEpochs {

    /** The current epoch, or {@link #UNKNOWN} if it cannot be read. */
    long current(String cacheName);

    /** Record that {@code cacheName} was invalidated. */
    void bump(String cacheName);

    /**
     * A cache that cannot be reached must not turn a correct read into an error, so an
     * unreadable epoch is treated as "no evidence of an eviction" rather than as a mismatch.
     */
    long UNKNOWN = -1L;

    final class InMemory implements CacheEpochs {

        private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

        @Override
        public long current(String cacheName) {
            AtomicLong counter = counters.get(cacheName);
            return counter == null ? 0L : counter.get();
        }

        @Override
        public void bump(String cacheName) {
            counters.computeIfAbsent(cacheName, name -> new AtomicLong()).incrementAndGet();
        }
    }
}
