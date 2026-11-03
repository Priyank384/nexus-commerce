package com.example.nexusCommerce.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * The epoch lives in Redis because every instance of the app shares one cache: a counter kept in
 * a single JVM would say nothing about an eviction performed by another, and the stale write it
 * is meant to catch would slip through.
 */
@Slf4j
public final class RedisCacheEpochs implements CacheEpochs {

    private final StringRedisTemplate redis;
    private final String prefix;

    public RedisCacheEpochs(StringRedisTemplate redis, String namespace) {
        this.redis = redis;
        this.prefix = namespace + ":cache-epoch:";
    }

    @Override
    public long current(String cacheName) {
        try {
            String value = redis.opsForValue().get(prefix + cacheName);
            return value == null ? 0L : Long.parseLong(value);
        } catch (RuntimeException e) {
            log.warn("Cache epoch for {} could not be read, caching stays permissive: {}", cacheName, e.getMessage());
            return UNKNOWN;
        }
    }

    @Override
    public void bump(String cacheName) {
        try {
            redis.opsForValue().increment(prefix + cacheName);
        } catch (RuntimeException e) {
            log.warn("Cache epoch for {} could not be bumped: {}", cacheName, e.getMessage());
        }
    }
}
