package com.example.nexusCommerce.security;

import java.time.Duration;
import java.time.Instant;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tracks access-token jtis that have been explicitly revoked (logout) before their natural
 * expiry. Entries live only as long as the token itself would have; after that the token
 * is expired and no check is needed.
 *
 * Both directions fail open when Redis is unreachable. That is a deliberate trade: the
 * alternative is that a cache blip turns every authenticated request, and every logout, into a
 * 500. The exposure is bounded to a token the holder already possessed, for at most the minutes
 * that token had left before it expired anyway -- the same window the signature check grants.
 * Anything that must not be replayed (refresh rotation, payment idempotency) is deliberately
 * kept fail-closed elsewhere.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    private static final String PREFIX = "jwt:blacklist::";

    private final StringRedisTemplate redis;

    public void revoke(String jti, Instant expiresAt) {
        long seconds = Math.max(1, Duration.between(Instant.now(), expiresAt).getSeconds());
        try {
            redis.opsForValue().set(PREFIX + jti, "1", Duration.ofSeconds(seconds));
        } catch (DataAccessException e) {
            // A logout that cannot record its revocation must still answer success; the caller
            // has already thrown the token away client-side.
            log.warn("Could not revoke token {}; it stays valid until it expires. {}", jti, e.getMessage());
        }
    }

    public boolean isRevoked(String jti) {
        if (jti == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(PREFIX + jti));
        } catch (DataAccessException e) {
            log.warn("Revocation list unreadable; treating token {} as valid. {}", jti, e.getMessage());
            return false;
        }
    }
}
