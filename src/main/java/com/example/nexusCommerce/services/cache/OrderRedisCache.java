package com.example.nexusCommerce.services.cache;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.example.nexusCommerce.dtos.GetOrderResponseDto;
import com.example.nexusCommerce.dtos.GetOrderSummaryResponseDto;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@AllArgsConstructor
@Slf4j
public class OrderRedisCache {

    private static final String KEY_ALL = "order:all";
    private static final String KEY_BY_ID = "order:by-id:";
    private static final String KEY_SUMMARY = "order:summary:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(1);

    private final StringRedisTemplate stringRedisTemplate;
    private ObjectMapper objectMapper;

    public Optional<List<GetOrderResponseDto>> getAll() {
        String responseJson = stringRedisTemplate.opsForValue().get(KEY_ALL);
        if (responseJson == null) {
            log.info("Cache miss for all orders");
            return Optional.empty();
        }
        log.info("Cache hit for all orders");
        try {
            List<GetOrderResponseDto> response = objectMapper.readValue(
                    responseJson, new TypeReference<List<GetOrderResponseDto>>() {});
            return Optional.of(response);
        } catch (Exception e) {
            log.error("Error parsing all orders from cache: {}", e.getMessage());
            stringRedisTemplate.delete(KEY_ALL);
            return Optional.empty();
        }
    }

    public void putAll(List<GetOrderResponseDto> orders) {
        try {
            stringRedisTemplate.opsForValue().set(KEY_ALL, objectMapper.writeValueAsString(orders), CACHE_TTL);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing all orders to cache" + e.getMessage());
        }
    }

    public Optional<GetOrderResponseDto> getById(Long id) {
        String responseJson = stringRedisTemplate.opsForValue().get(KEY_BY_ID + id);
        if (responseJson == null) {
            log.info("Cache miss for order: {}", id);
            return Optional.empty();
        }
        log.info("Cache hit for order: {}", id);
        try {
            GetOrderResponseDto response = objectMapper.readValue(responseJson, GetOrderResponseDto.class);
            return Optional.of(response);
        } catch (Exception e) {
            log.error("Error parsing order from cache: {}", e.getMessage());
            stringRedisTemplate.delete(KEY_BY_ID + id);
            return Optional.empty();
        }
    }

    public void putById(Long id, GetOrderResponseDto order) {
        try {
            stringRedisTemplate.opsForValue().set(KEY_BY_ID + id, objectMapper.writeValueAsString(order), CACHE_TTL);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing order to cache" + e.getMessage());
        }
    }

    public Optional<GetOrderSummaryResponseDto> getSummary(Long id) {
        String responseJson = stringRedisTemplate.opsForValue().get(KEY_SUMMARY + id);
        if (responseJson == null) {
            log.info("Cache miss for order summary: {}", id);
            return Optional.empty();
        }
        log.info("Cache hit for order summary: {}", id);
        try {
            GetOrderSummaryResponseDto response = objectMapper.readValue(responseJson, GetOrderSummaryResponseDto.class);
            return Optional.of(response);
        } catch (Exception e) {
            log.error("Error parsing order summary from cache: {}", e.getMessage());
            stringRedisTemplate.delete(KEY_SUMMARY + id);
            return Optional.empty();
        }
    }

    public void putSummary(Long id, GetOrderSummaryResponseDto summary) {
        try {
            stringRedisTemplate.opsForValue().set(KEY_SUMMARY + id, objectMapper.writeValueAsString(summary), CACHE_TTL);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing order summary to cache" + e.getMessage());
        }
    }
}
