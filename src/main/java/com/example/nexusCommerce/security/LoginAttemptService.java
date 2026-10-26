package com.example.nexusCommerce.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Per-account failed-login counter, complementing the per-IP limiter: a credential-stuffing
 * run spread across many addresses still gets capped once it targets one account.
 *
 * <p>Keys are SHA-256 of the normalised email so the keyspace never holds customer PII.
 * Like the IP limiter it fails open: an unreadable counter must not lock honest users out
 * while Redis is unavailable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final String PREFIX = "login-fail::";

    private final ObjectProvider<StringRedisTemplate> redisProvider;

    @Value("${app.rate-limit.login.max:10}")
    private int maxFailures;

    @Value("${app.rate-limit.login.window-seconds:60}")
    private int windowSeconds;

    public boolean isBlocked(String email) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) return false;
        try {
            String count = redis.opsForValue().get(key(email));
            return count != null && Integer.parseInt(count) >= maxFailures;
        } catch (DataAccessException | NumberFormatException e) {
            log.warn("Login failure counter unreadable; allowing the attempt. {}", e.getMessage());
            return false;
        }
    }

    public void recordFailure(String email) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) return;
        try {
            String key = key(email);
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofSeconds(windowSeconds));
            }
        } catch (DataAccessException e) {
            log.warn("Login failure counter unwritable; attempt not counted. {}", e.getMessage());
        }
    }

    public void clear(String email) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) return;
        try {
            redis.delete(key(email));
        } catch (DataAccessException e) {
            log.warn("Login failure counter not cleared. {}", e.getMessage());
        }
    }

    private static String key(String email) {
        return PREFIX + sha256(email);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JVM", e);
        }
    }
}
