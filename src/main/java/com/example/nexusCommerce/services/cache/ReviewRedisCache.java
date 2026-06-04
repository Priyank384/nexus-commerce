package com.example.nexusCommerce.services.cache;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.example.nexusCommerce.dtos.GetReviewResponseDto;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@AllArgsConstructor
@Slf4j
public class ReviewRedisCache {

    private static final String KEY_ALL = "review:all";
    private static final String KEY_BY_ID = "review:by-id:";
    private static final String KEY_BY_PRODUCT = "review:by-product:";
    private static final String KEY_BY_ORDER = "review:by-order:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(1);

    private final StringRedisTemplate stringRedisTemplate;
    private ObjectMapper objectMapper;

    public Optional<List<GetReviewResponseDto>> getAll() {
        String responseJson = stringRedisTemplate.opsForValue().get(KEY_ALL);
        if (responseJson == null) {
            log.info("Cache miss for all reviews");
            return Optional.empty();
        }
        log.info("Cache hit for all reviews");
        try {
            List<GetReviewResponseDto> response = objectMapper.readValue(
                    responseJson, new TypeReference<List<GetReviewResponseDto>>() {});
            return Optional.of(response);
        } catch (Exception e) {
            log.error("Error parsing all reviews from cache: {}", e.getMessage());
            stringRedisTemplate.delete(KEY_ALL);
            return Optional.empty();
        }
    }

    public void putAll(List<GetReviewResponseDto> reviews) {
        try {
            stringRedisTemplate.opsForValue().set(KEY_ALL, objectMapper.writeValueAsString(reviews), CACHE_TTL);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing all reviews to cache" + e.getMessage());
        }
    }

    public Optional<GetReviewResponseDto> getById(Long id) {
        String responseJson = stringRedisTemplate.opsForValue().get(KEY_BY_ID + id);
        if (responseJson == null) {
            log.info("Cache miss for review: {}", id);
            return Optional.empty();
        }
        log.info("Cache hit for review: {}", id);
        try {
            GetReviewResponseDto response = objectMapper.readValue(responseJson, GetReviewResponseDto.class);
            return Optional.of(response);
        } catch (Exception e) {
            log.error("Error parsing review from cache: {}", e.getMessage());
            stringRedisTemplate.delete(KEY_BY_ID + id);
            return Optional.empty();
        }
    }

    public void putById(Long id, GetReviewResponseDto review) {
        try {
            stringRedisTemplate.opsForValue().set(KEY_BY_ID + id, objectMapper.writeValueAsString(review), CACHE_TTL);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing review to cache" + e.getMessage());
        }
    }

    public Optional<List<GetReviewResponseDto>> getByProductId(Long productId) {
        String responseJson = stringRedisTemplate.opsForValue().get(KEY_BY_PRODUCT + productId);
        if (responseJson == null) {
            log.info("Cache miss for reviews by product: {}", productId);
            return Optional.empty();
        }
        log.info("Cache hit for reviews by product: {}", productId);
        try {
            List<GetReviewResponseDto> response = objectMapper.readValue(
                    responseJson, new TypeReference<List<GetReviewResponseDto>>() {});
            return Optional.of(response);
        } catch (Exception e) {
            log.error("Error parsing reviews by product from cache: {}", e.getMessage());
            stringRedisTemplate.delete(KEY_BY_PRODUCT + productId);
            return Optional.empty();
        }
    }

    public void putByProductId(Long productId, List<GetReviewResponseDto> reviews) {
        try {
            stringRedisTemplate.opsForValue().set(
                    KEY_BY_PRODUCT + productId, objectMapper.writeValueAsString(reviews), CACHE_TTL);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing reviews by product to cache" + e.getMessage());
        }
    }

    public Optional<List<GetReviewResponseDto>> getByOrderId(Long orderId) {
        String responseJson = stringRedisTemplate.opsForValue().get(KEY_BY_ORDER + orderId);
        if (responseJson == null) {
            log.info("Cache miss for reviews by order: {}", orderId);
            return Optional.empty();
        }
        log.info("Cache hit for reviews by order: {}", orderId);
        try {
            List<GetReviewResponseDto> response = objectMapper.readValue(
                    responseJson, new TypeReference<List<GetReviewResponseDto>>() {});
            return Optional.of(response);
        } catch (Exception e) {
            log.error("Error parsing reviews by order from cache: {}", e.getMessage());
            stringRedisTemplate.delete(KEY_BY_ORDER + orderId);
            return Optional.empty();
        }
    }

    public void putByOrderId(Long orderId, List<GetReviewResponseDto> reviews) {
        try {
            stringRedisTemplate.opsForValue().set(
                    KEY_BY_ORDER + orderId, objectMapper.writeValueAsString(reviews), CACHE_TTL);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing reviews by order to cache" + e.getMessage());
        }
    }
}
