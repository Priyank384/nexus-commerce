package com.example.nexusCommerce.configs;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableCaching
@Slf4j
public class CacheConfig implements CachingConfigurer {

    public static final String CATEGORY_ALL = "category:all";
    public static final String CATEGORY_BY_ID = "category:by-id";

    public static final String PRODUCT_ALL = "product:all";
    public static final String PRODUCT_BY_ID = "product:by-id";
    public static final String PRODUCT_DETAILS = "product:details";
    public static final String PRODUCT_BY_CATEGORY = "product:by-category";
    public static final String PRODUCT_UNIQUE_CATEGORIES = "product:unique-categories";

    public static final String ORDER_ALL = "order:all";
    public static final String ORDER_BY_ID = "order:by-id";
    public static final String ORDER_SUMMARY = "order:summary";

    public static final String REVIEW_ALL = "review:all";
    public static final String REVIEW_BY_ID = "review:by-id";
    public static final String REVIEW_BY_PRODUCT = "review:by-product";
    public static final String REVIEW_BY_ORDER = "review:by-order";

    private static final Duration LIST_TTL = Duration.ofMinutes(5);
    private static final Duration ITEM_TTL = Duration.ofMinutes(10);
    private static final Duration AGGREGATE_TTL = Duration.ofMinutes(15);

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
    public RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        // Null values are cached by default, which is what we want: a request for a
        // non-existent id must not reach the database on every call.
        RedisCacheConfiguration list = baseConfig(LIST_TTL);

        Map<String, RedisCacheConfiguration> perCache = Map.ofEntries(
                Map.entry(CATEGORY_ALL, list),
                Map.entry(CATEGORY_BY_ID, baseConfig(ITEM_TTL)),
                Map.entry(PRODUCT_ALL, list),
                Map.entry(PRODUCT_BY_ID, baseConfig(ITEM_TTL)),
                Map.entry(PRODUCT_DETAILS, baseConfig(ITEM_TTL)),
                Map.entry(PRODUCT_BY_CATEGORY, list),
                Map.entry(PRODUCT_UNIQUE_CATEGORIES, baseConfig(AGGREGATE_TTL)),
                Map.entry(ORDER_ALL, list),
                Map.entry(ORDER_BY_ID, baseConfig(ITEM_TTL)),
                Map.entry(ORDER_SUMMARY, baseConfig(ITEM_TTL)),
                Map.entry(REVIEW_ALL, list),
                Map.entry(REVIEW_BY_ID, baseConfig(ITEM_TTL)),
                Map.entry(REVIEW_BY_PRODUCT, list),
                Map.entry(REVIEW_BY_ORDER, list));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(list)
                .withInitialCacheConfigurations(perCache)
                // Defer put/evict/clear until the surrounding transaction commits, so a
                // concurrent reader cannot repopulate a cache with pre-commit state.
                .transactionAware()
                .build();
    }

    // Boot 4 only auto-configures a CacheManager through the cache starter, which this
    // app does not need; tests set spring.cache.type=simple and get this manager instead.
    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "simple")
    public ConcurrentMapCacheManager simpleCacheManager() {
        return new ConcurrentMapCacheManager(CATEGORY_ALL, CATEGORY_BY_ID,
                PRODUCT_ALL, PRODUCT_BY_ID, PRODUCT_DETAILS, PRODUCT_BY_CATEGORY, PRODUCT_UNIQUE_CATEGORIES,
                ORDER_ALL, ORDER_BY_ID, ORDER_SUMMARY,
                REVIEW_ALL, REVIEW_BY_ID, REVIEW_BY_PRODUCT, REVIEW_BY_ORDER);
    }

    /**
     * Every entry written by one request shares a TTL, so a warm cache would expire in
     * lockstep and dump a whole traffic wave onto MySQL. Each key gets its own jitter
     * inside a +/-10% window.
     */
    private static RedisCacheWriter.TtlFunction jittered(Duration base) {
        long spread = Math.max(base.toMillis() / 10, 1L);
        return (key, value) -> Duration.ofMillis(base.toMillis() - spread / 2
                + ThreadLocalRandom.current().nextLong(spread));
    }

    private static RedisCacheConfiguration baseConfig(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(jittered(ttl))
                .computePrefixWith(name -> name + ":")
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(valueSerializer()));
    }

    /**
     * Cached values are polymorphic (List<Dto>, NullValue), so the type has to be written
     * into the payload. The validator restricts deserialization to our own DTOs and the
     * JDK collections they need, which is what makes this safe against a poisoned cache.
     */
    private static GenericJacksonJsonRedisSerializer valueSerializer() {
        return GenericJacksonJsonRedisSerializer.create(builder -> builder
                .enableSpringCacheNullValueSupport()
                .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .allowIfSubType("com.example.nexusCommerce.")
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.math.")
                        .allowIfSubType("java.time.")
                        .allowIfSubTypeIsArray()
                        .build()));
    }

    /**
     * A cache outage must not take the API down with it: the read falls through to the
     * database and the request still succeeds.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache get failed for {}:{} - serving from database. {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache put failed for {}:{} - {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache evict failed for {}:{} - stale entry may survive until TTL", cache.getName(), key);
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Cache clear failed for {} - stale entries may survive until TTL", cache.getName());
            }
        };
    }
}
