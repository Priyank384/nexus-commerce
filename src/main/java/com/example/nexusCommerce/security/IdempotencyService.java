package com.example.nexusCommerce.security;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.example.nexusCommerce.dtos.GetOrderResponseDto;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.utils.ApiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

/**
 * Redis-backed idempotency for order-creating POST endpoints. The client sends an
 * {@code Idempotency-Key} header; retries with the same key replay the first response
 * instead of placing a duplicate order.
 *
 * <p>Keys are scoped by the caller's user id AND the endpoint family so a leaked UUID
 * cannot cross user boundaries or collide with a differently-shaped call.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String PREFIX = "idem::";
    private static final String IN_FLIGHT = "__IN_FLIGHT__";
    private static final Duration IN_FLIGHT_TTL = Duration.ofMinutes(10);
    private static final Duration RESULT_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    // Self-owned mapper: ObjectMapper isn't a bean in every Spring test context that
    // boots this service (cache-only integration tests), and its only consumer here
    // is our internal Redis payload -- not the HTTP layer.
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .registerModule(new JavaTimeModule());

    /**
     * Returns the cached response if the same caller+scope+key completed earlier, empty
     * if this call is the first (and the caller has now been granted the lock), and
     * throws if a concurrent request with the same key is still running.
     */
    public Optional<ApiResponse<GetOrderResponseDto>> tryStart(Long userId, String scope, String key) {
        String redisKey = redisKey(userId, scope, key);
        Boolean acquired = redis.opsForValue().setIfAbsent(redisKey, IN_FLIGHT, IN_FLIGHT_TTL);
        if (Boolean.TRUE.equals(acquired)) {
            return Optional.empty();
        }
        String existing = redis.opsForValue().get(redisKey);
        if (IN_FLIGHT.equals(existing)) {
            throw new InvalidRequestException("A request with this Idempotency-Key is still being processed");
        }
        if (existing == null) {
            // Evicted between SETNX and GET. Re-acquire with SETNX so a parallel caller
            // that also observed null cannot both proceed -- plain set() would race.
            Boolean reacquired = redis.opsForValue().setIfAbsent(redisKey, IN_FLIGHT, IN_FLIGHT_TTL);
            if (Boolean.TRUE.equals(reacquired)) {
                return Optional.empty();
            }
            String after = redis.opsForValue().get(redisKey);
            if (after == null || IN_FLIGHT.equals(after)) {
                throw new InvalidRequestException("A request with this Idempotency-Key is still being processed");
            }
            return Optional.of(deserialize(after));
        }
        return Optional.of(deserialize(existing));
    }

    public void complete(Long userId, String scope, String key, ApiResponse<GetOrderResponseDto> response) {
        redis.opsForValue().set(redisKey(userId, scope, key), serialize(response), RESULT_TTL);
    }

    /** Best-effort release so a subsequent retry can create the order if the first attempt failed. */
    public void release(Long userId, String scope, String key) {
        String redisKey = redisKey(userId, scope, key);
        String existing = redis.opsForValue().get(redisKey);
        if (IN_FLIGHT.equals(existing)) redis.delete(redisKey);
    }

    private String redisKey(Long userId, String scope, String key) {
        return PREFIX + userId + "::" + scope + "::" + key;
    }

    @SneakyThrows
    private String serialize(ApiResponse<GetOrderResponseDto> body) {
        return objectMapper.writeValueAsString(body);
    }

    @SneakyThrows
    private ApiResponse<GetOrderResponseDto> deserialize(String json) {
        return objectMapper.readValue(json, new TypeReference<ApiResponse<GetOrderResponseDto>>() {});
    }
}
