package com.example.nexusCommerce.security;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Opaque, single-use refresh tokens stored in Redis and bound to a user id. Rotating on
 * every refresh means a stolen refresh token is usable at most once, and a leaked one
 * is caught the next time the real caller tries to refresh.
 *
 * <p>Tokens are also indexed per user, which is the only way to sign every device out at
 * once: the keys are named by token, not by owner, so without the index a password change
 * would have to scan the whole keyspace.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String PREFIX = "refresh::";
    private static final String USER_PREFIX = "refresh-user::";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    @Value("${app.jwt.refresh-expiration-days:7}")
    private long ttlDays;

    public String issue(Long userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(PREFIX + token, String.valueOf(userId), Duration.ofDays(ttlDays));
        String index = USER_PREFIX + userId;
        redis.opsForSet().add(index, token);
        // Refreshed on every issue so the index never outlives the newest token it names.
        redis.expire(index, Duration.ofDays(ttlDays));
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
        Long userId = Long.valueOf(uid);
        unindex(userId, token);
        return userId;
    }

    public void revoke(String token) {
        if (token == null || token.isBlank()) return;
        String uid = redis.opsForValue().get(PREFIX + token);
        redis.delete(PREFIX + token);
        if (uid != null) {
            unindex(Long.valueOf(uid), token);
        }
    }

    /**
     * Drops every refresh token held by one account. Used when the password changes: the
     * sessions other devices hold must not survive it. Access tokens are not touched, so a
     * stolen bearer stays usable for at most the remaining JWT TTL while every refresh dies.
     */
    public void revokeAllForUser(Long userId) {
        String index = USER_PREFIX + userId;
        Set<String> tokens = redis.opsForSet().members(index);
        if (tokens != null && !tokens.isEmpty()) {
            redis.delete(tokens.stream().map(token -> PREFIX + token).toList());
        }
        redis.delete(index);
    }

    private void unindex(Long userId, String token) {
        redis.opsForSet().remove(USER_PREFIX + userId, token);
    }
}
