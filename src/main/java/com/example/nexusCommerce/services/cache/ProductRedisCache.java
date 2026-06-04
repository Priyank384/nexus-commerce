package com.example.nexusCommerce.services.cache;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.example.nexusCommerce.dtos.GetProductResponseDto;
import com.example.nexusCommerce.dtos.GetProductWithDetailsResponseDto;
import com.example.nexusCommerce.schema.Product;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@AllArgsConstructor
@Slf4j
public class ProductRedisCache {
    private static final String KEY_SUMMARY = "product:summary:";
    private static final String KEY_ALL_SUMMARIES = "product:summary:all";
    private static final String KEY_DETAILS = "product:details:";
    private static final String KEY_BY_CATEGORY = "product:category:";
    private static final String KEY_UNIQUE_CATEGORIES = "product:unique-categories";
    private static final Duration CACHE_TTL = Duration.ofMinutes(1);
    private final StringRedisTemplate stringRedisTemplate;
    private ObjectMapper objectMapper;

    public Optional<GetProductResponseDto> getSummary(Long id){
        String responseJson = stringRedisTemplate.opsForValue().get(KEY_SUMMARY + id);
        if(responseJson==null){
            log.info("Cache miss for product summary: {}", id);
            return Optional.empty();
        }
        log.info("Cache hit for product summary: {}", id);
        try {
            GetProductResponseDto response = objectMapper.readValue(responseJson, GetProductResponseDto.class);
            return Optional.of(response);
        } catch (Exception e) {
            log.error("Error parsing product summary from cache: {}", e.getMessage());
            stringRedisTemplate.delete(KEY_SUMMARY + id);
            return Optional.empty();
        }
    }

    public void putSummary(Long id, GetProductResponseDto response){
        try {
            stringRedisTemplate.opsForValue().set(KEY_SUMMARY + id, objectMapper.writeValueAsString(response), CACHE_TTL);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing product summary to cache" + e.getMessage());
        }
    }

    public Optional<List<GetProductResponseDto>> getAllSummaries(){
        String responseJson = stringRedisTemplate.opsForValue().get(KEY_ALL_SUMMARIES);
        if(responseJson==null){
            log.info("Cache miss for all product summaries");
            return Optional.empty();
        }
        log.info("Cache hit for all product summaries");
        try {
            List<GetProductResponseDto> response = objectMapper.readValue(
                    responseJson, new TypeReference<List<GetProductResponseDto>>() {});
            return Optional.of(response);
        } catch (Exception e) {
            log.error("Error parsing all product summaries from cache: {}", e.getMessage());
            stringRedisTemplate.delete(KEY_ALL_SUMMARIES);
            return Optional.empty();
        }
    }

    public void putAllSummaries(List<GetProductResponseDto> summaries){
        try {
            stringRedisTemplate.opsForValue().set(KEY_ALL_SUMMARIES, objectMapper.writeValueAsString(summaries), CACHE_TTL);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing all product summaries to cache" + e.getMessage());
        }
    }

    public Optional<GetProductWithDetailsResponseDto> getDetails(Long id){
        String responseJson = stringRedisTemplate.opsForValue().get(KEY_DETAILS + id);
        if(responseJson==null){
            log.info("Cache miss for product details: {}", id);
            return Optional.empty();
        }
        log.info("Cache hit for product details: {}", id);
        try {
            GetProductWithDetailsResponseDto response = objectMapper.readValue(
                    responseJson, GetProductWithDetailsResponseDto.class);
            return Optional.of(response);
        } catch (Exception e) {
            log.error("Error parsing product details from cache: {}", e.getMessage());
            stringRedisTemplate.delete(KEY_DETAILS + id);
            return Optional.empty();
        }
    }

    public void putDetails(Long id, GetProductWithDetailsResponseDto response){
        try {
            stringRedisTemplate.opsForValue().set(KEY_DETAILS + id, objectMapper.writeValueAsString(response), CACHE_TTL);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing product details to cache" + e.getMessage());
        }
    }

    public Optional<List<Product>> getByCategory(String categoryName){
        String key = KEY_BY_CATEGORY + categoryName.trim().toLowerCase(Locale.ROOT);
        String responseJson = stringRedisTemplate.opsForValue().get(key);
        if(responseJson==null){
            log.info("Cache miss for products by category: {}", categoryName);
            return Optional.empty();
        }
        log.info("Cache hit for products by category: {}", categoryName);
        try {
            List<Product> response = objectMapper.readValue(responseJson, new TypeReference<List<Product>>() {});
            return Optional.of(response);
        } catch (Exception e) {
            log.error("Error parsing products by category from cache: {}", e.getMessage());
            stringRedisTemplate.delete(key);
            return Optional.empty();
        }
    }

    public void putByCategory(String categoryName, List<Product> products){
        String key = KEY_BY_CATEGORY + categoryName.trim().toLowerCase(Locale.ROOT);
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(products), CACHE_TTL);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing products by category to cache" + e.getMessage());
        }
    }

    public Optional<List<String>> getUniqueCategories(){
        String responseJson = stringRedisTemplate.opsForValue().get(KEY_UNIQUE_CATEGORIES);
        if(responseJson==null){
            log.info("Cache miss for unique categories");
            return Optional.empty();
        }
        log.info("Cache hit for unique categories");
        try {
            List<String> response = objectMapper.readValue(responseJson, new TypeReference<List<String>>() {});
            return Optional.of(response);
        } catch (Exception e) {
            log.error("Error parsing unique categories from cache: {}", e.getMessage());
            stringRedisTemplate.delete(KEY_UNIQUE_CATEGORIES);
            return Optional.empty();
        }
    }

    public void putUniqueCategories(List<String> categories){
        try {
            stringRedisTemplate.opsForValue().set(KEY_UNIQUE_CATEGORIES, objectMapper.writeValueAsString(categories), CACHE_TTL);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing unique categories to cache" + e.getMessage());
        }
    }
}
