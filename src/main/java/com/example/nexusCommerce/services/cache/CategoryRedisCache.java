package com.example.nexusCommerce.services.cache;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.example.nexusCommerce.schema.Category;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@AllArgsConstructor
@Slf4j
public class CategoryRedisCache {

    private static final String KEY_ALL = "category:all";
    private static final String KEY_BY_ID = "category:by-id:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(1);

    private final StringRedisTemplate stringRedisTemplate;
    private ObjectMapper objectMapper;

    public Optional<List<Category>> getAll() {
        String responseJson = stringRedisTemplate.opsForValue().get(KEY_ALL);
        if (responseJson == null) {
            log.info("Cache miss for all categories");
            return Optional.empty();
        }
        log.info("Cache hit for all categories");
        try {
            List<Category> response = objectMapper.readValue(responseJson, new TypeReference<List<Category>>() {});
            return Optional.of(response);
        } catch (Exception e) {
            log.error("Error parsing all categories from cache: {}", e.getMessage());
            stringRedisTemplate.delete(KEY_ALL);
            return Optional.empty();
        }
    }

    public void putAll(List<Category> categories) {
        try {
            stringRedisTemplate.opsForValue().set(KEY_ALL, objectMapper.writeValueAsString(categories), CACHE_TTL);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing all categories to cache" + e.getMessage());
        }
    }

    public Optional<Category> getById(Long id) {
        String responseJson = stringRedisTemplate.opsForValue().get(KEY_BY_ID + id);
        if (responseJson == null) {
            log.info("Cache miss for category: {}", id);
            return Optional.empty();
        }
        log.info("Cache hit for category: {}", id);
        try {
            Category response = objectMapper.readValue(responseJson, Category.class);
            return Optional.of(response);
        } catch (Exception e) {
            log.error("Error parsing category from cache: {}", e.getMessage());
            stringRedisTemplate.delete(KEY_BY_ID + id);
            return Optional.empty();
        }
    }

    public void putById(Long id, Category category) {
        try {
            stringRedisTemplate.opsForValue().set(KEY_BY_ID + id, objectMapper.writeValueAsString(category), CACHE_TTL);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing category to cache" + e.getMessage());
        }
    }
}
