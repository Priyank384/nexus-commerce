package com.example.nexusCommerce.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Short-lived, single-use password reset tokens held in Redis and bound to a user id.
 *
 * <p>Redis rather than a column: the token is worthless the moment it is used or expires,
 * so nothing here is worth a migration, and the store disappears with a cache flush instead
 * of lingering in the database. Unlike the login counters this one does not fail open: a
 * token that cannot be written is a link that cannot be honoured, so a Redis outage has to
 * surface rather than mail a dead URL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final String PREFIX = "pwreset::";
    private static final String COOLDOWN_PREFIX = "pwreset-cool::";
    /** Which token this account's newest link carries, so issuing a second one retires the first. */
    private static final String USER_PREFIX = "pwreset-user::";

    private final StringRedisTemplate redis;

    @Value("${app.auth.reset-token-ttl-minutes:15}")
    private long ttlMinutes;

    @Value("${app.auth.reset-send-cooldown-seconds:60}")
    private long cooldownSeconds;

    /**
     * Returns a token to mail, or null when this account was emailed inside the cooldown
     * window. Overwriting the stored token without sending would invalidate the link the
     * shopper already has, so a throttled request stays silent and the first link keeps working.
     */
    public String issue(Long userId) {
        Boolean first = redis.opsForValue().setIfAbsent(
                COOLDOWN_PREFIX + userId, "1", Duration.ofSeconds(cooldownSeconds));
        if (!Boolean.TRUE.equals(first)) {
            log.info("Password reset for user {} is inside the send cooldown; earlier link still valid", userId);
            return null;
        }
        String token = OpaqueToken.mint();
        Duration ttl = Duration.ofMinutes(ttlMinutes);

        // A link the account owner has already replaced is a credential they cannot revoke: every
        // reset request would otherwise leave another live token behind for the whole TTL.
        String previous = redis.opsForValue().getAndDelete(USER_PREFIX + userId);
        if (previous != null) {
            redis.delete(PREFIX + previous);
        }

        redis.opsForValue().set(PREFIX + token, String.valueOf(userId), ttl);
        redis.opsForValue().set(USER_PREFIX + userId, token, ttl);
        return token;
    }

    /** GETDEL makes a link usable exactly once, so a replayed reset cannot re-run on the same token. */
    public Long consume(String token) {
        if (token == null || token.isBlank()) return null;
        String uid = redis.opsForValue().getAndDelete(PREFIX + token);
        if (uid == null) return null;
        // Leave the index alone when a newer link has already replaced this one.
        if (token.equals(redis.opsForValue().get(USER_PREFIX + uid))) {
            redis.delete(USER_PREFIX + uid);
        }
        return Long.valueOf(uid);
    }
}
