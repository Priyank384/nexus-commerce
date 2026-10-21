package com.example.nexusCommerce.security;

import java.time.Duration;
import java.time.Instant;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Tracks access-token jtis that have been explicitly revoked (logout) before their natural
 * expiry. Entries live only as long as the token itself would have; after that the token
 * is expired and no check is needed.
 */
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String PREFIX = "jwt:blacklist::";

    private final StringRedisTemplate redis;

    public void revoke(String jti, Instant expiresAt) {
        long seconds = Math.max(1, Duration.between(Instant.now(), expiresAt).getSeconds());
        redis.opsForValue().set(PREFIX + jti, "1", Duration.ofSeconds(seconds));
    }

    public boolean isRevoked(String jti) {
        return jti != null && Boolean.TRUE.equals(redis.hasKey(PREFIX + jti));
    }
}
