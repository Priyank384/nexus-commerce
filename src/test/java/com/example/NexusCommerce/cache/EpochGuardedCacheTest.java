package com.example.nexusCommerce.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache.ValueWrapper;
import org.springframework.cache.concurrent.ConcurrentMapCache;

/**
 * The race this guard exists for: a reader misses the cache, its database load runs while a
 * writer commits and evicts, and the reader then stores what it read beforehand. Those tests
 * drive the interleaving by hand because no timing trick reproduces it on command.
 */
class EpochGuardedCacheTest {

    private static final String NAME = "category:tree";

    private final ConcurrentMapCache delegate = new ConcurrentMapCache(NAME);
    private final CacheEpochs.InMemory epochs = new CacheEpochs.InMemory();
    private final EpochGuardedCache cache = new EpochGuardedCache(delegate, epochs);

    @Test
    void aPlainMissIsStoredWhenNothingEvictedInBetween() {
        assertNull(cache.get("key"));

        cache.put("key", "value");

        assertEquals("value", delegate.get("key").get());
    }

    @Test
    void aValueLoadedAcrossAnEvictionIsNotStored() {
        assertNull(cache.get("key"));

        // The eviction lands after the miss was observed but before the value comes back.
        epochs.bump(NAME);
        cache.put("key", "stale");

        assertNull(delegate.get("key"));
    }

    @Test
    void aCacheHitDoesNotLeaveAMissToSuppressTheNextWrite() {
        cache.put("key", "warm");

        ValueWrapper hit = cache.get("key");
        epochs.bump(NAME);
        cache.put("key", "fresh");

        assertEquals("warm", hit.get());
        assertEquals("fresh", delegate.get("key").get());
    }

    @Test
    void theSyncPathRemovesAnEntryItsOwnLoadRaced() {
        Callable<String> loader = () -> {
            epochs.bump(NAME);
            return "loaded";
        };

        assertEquals("loaded", cache.get("key", loader));

        assertNull(delegate.get("key"));
    }

    @Test
    void theSyncPathKeepsAnEntryLoadedWithoutAnEviction() {
        assertEquals("loaded", cache.get("key", () -> "loaded"));

        assertEquals("loaded", delegate.get("key").get());
    }

    @Test
    void aSyncCacheHitDoesNotPayForTheGuard() {
        AtomicInteger epochReads = new AtomicInteger();
        CacheEpochs counted = new CacheEpochs() {
            @Override
            public long current(String cacheName) {
                epochReads.incrementAndGet();
                return 0L;
            }

            @Override
            public void bump(String cacheName) {
            }
        };
        EpochGuardedCache guarded = new EpochGuardedCache(delegate, counted);

        assertEquals("first", guarded.get("key", () -> "first"));
        int readsWhileCold = epochReads.get();

        assertEquals("first", guarded.get("key", () -> "second"));

        assertEquals(readsWhileCold, epochReads.get());
    }

    @Test
    void anUnreadableEpochLeavesCachingAlone() {
        ConcurrentMapCache target = new ConcurrentMapCache(NAME);
        CacheEpochs unreadable = new CacheEpochs() {
            @Override
            public long current(String cacheName) {
                return UNKNOWN;
            }

            @Override
            public void bump(String cacheName) {
            }
        };
        EpochGuardedCache guarded = new EpochGuardedCache(target, unreadable);

        assertNull(guarded.get("key"));
        guarded.put("key", "value");

        assertEquals("value", target.get("key").get());
    }
}
