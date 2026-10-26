package com.example.nexusCommerce.security;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Opaque, single-use refresh tokens stored in Redis and bound to a user id. Rotating on
 * every refresh means a stolen refresh token is usable at most once, and a leaked one
 * is caught the next time the real caller tries to refresh.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String PREFIX = "refresh::";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    @Value("${app.jwt.refresh-expiration-days:7}")
    private long ttlDays;

    public String issue(Long userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(PREFIX + token, String.valueOf(userId), Duration.ofDays(ttlDays));
        return token;
    }

    /**
     * Consumes the supplied token (single-use) and returns the bound user id, or null if
     * the token is unknown/already used. Rotation happens automatically because the caller
     * issues a new token right after.
     *
     * <p>GETDEL is a single round trip, so two concurrent refreshes with the same token
     * cannot both observe it: exactly one caller wins and the other is treated as a replay.
     */
    public Long consume(String token) {
        if (token == null || token.isBlank()) return null;
        String uid = redis.opsForValue().getAndDelete(PREFIX + token);
        if (uid == null) return null;
        return Long.valueOf(uid);
    }

    public void revoke(String token) {
        if (token != null && !token.isBlank()) redis.delete(PREFIX + token);
    }
}
