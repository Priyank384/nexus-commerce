package com.example.nexusCommerce.security;

import java.io.IOException;
import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fixed-window rate limiter keyed on the caller's IP for the unauthenticated auth surface.
 *
 * <p>Only two kinds of request are counted: a sign-in attempt that was rejected, and every
 * registration attempt. A successful sign-in costs nothing and token refresh is not limited at
 * all. The storefront reaches Spring through one BFF, so every shopper shares that server's
 * egress IP: the browser refreshes its session on each full page load, and counting those (or
 * successful sign-ins) would exhaust a shared bucket during ordinary browsing. Failed sign-ins
 * are what a credential-stuffing run looks like, and registration is an unauthenticated write.
 *
 * <p>Fails open when Redis is unreachable - see TokenBlacklistService for the same
 * reasoning: a soft control should not take sign-in down with the cache.
 *
 * <p>Redis access is resolved lazily via ObjectProvider: the interceptor bean
 * itself must always exist (otherwise web-slice tests without Redis fail
 * to wire the chain), but preHandle is a no-op when no StringRedisTemplate
 * is registered at runtime.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoginRateLimiterInterceptor implements HandlerInterceptor {

    private static final String PREFIX = "rate::login::";
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String REGISTER_PATH = "/api/v1/users";

    private final ObjectProvider<StringRedisTemplate> redisProvider;

    @Value("${app.rate-limit.login.max:10}")
    private int maxAttempts;

    @Value("${app.rate-limit.login.window-seconds:60}")
    private int windowSeconds;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!limited(request)) {
            return true;
        }
        String ip = clientIp(request);
        Long count = LOGIN_PATH.equals(request.getRequestURI()) ? read(ip) : increment(ip);
        if (count == null) {
            return true;
        }
        boolean overLimit = LOGIN_PATH.equals(request.getRequestURI())
                ? count >= maxAttempts
                : count > maxAttempts;
        return overLimit ? reject(response) : true;
    }

    /**
     * Sign-in failures are the only login requests that consume the bucket, and the status is
     * only known once the endpoint has run. {@code afterCompletion} still fires for a rejected
     * login, so this is the one place that can tell the two apart.
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        if (LOGIN_PATH.equals(request.getRequestURI())
                && response.getStatus() == HttpStatus.UNAUTHORIZED.value()) {
            increment(clientIp(request));
        }
    }

    /** Refresh is deliberately absent: it is a session operation, not a credential guess. */
    private static boolean limited(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (LOGIN_PATH.equals(uri)) {
            return true;
        }
        // /api/v1/users is shared with GET /me and the admin list; only its POST (register)
        // is an unauthenticated, brute-forceable write.
        return REGISTER_PATH.equals(uri) && "POST".equalsIgnoreCase(request.getMethod());
    }

    private Long increment(String ip) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) return null;
        try {
            String key = PREFIX + ip;
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofSeconds(windowSeconds));
            }
            return count;
        } catch (DataAccessException e) {
            // Best-effort control, so it fails open: an unreadable counter must not lock every
            // honest shopper out of signing in while Redis is having a bad minute.
            log.warn("Login rate limit counter unreadable for {}; allowing the attempt. {}", ip, e.getMessage());
            return null;
        }
    }

    private Long read(String ip) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) return null;
        try {
            String value = redis.opsForValue().get(PREFIX + ip);
            return value == null ? 0L : Long.parseLong(value);
        } catch (DataAccessException | NumberFormatException e) {
            log.warn("Login rate limit counter unreadable for {}; allowing the attempt. {}", ip, e.getMessage());
            return null;
        }
    }

    private boolean reject(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"success\":false,\"message\":\"Too many attempts\",\"error\":\"Rate limit exceeded\",\"data\":null}");
        return false;
    }

    /**
     * The right-most entry is appended by the proxy nearest to us, so it is the only one we
     * can trust; the left-most is whatever the caller chose to send and is trivially spoofed
     * to get a fresh bucket per request.
     */
    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] hops = xff.split(",");
            for (int i = hops.length - 1; i >= 0; i--) {
                String candidate = hops[i].trim();
                if (!candidate.isEmpty()) {
                    return candidate;
                }
            }
        }
        return req.getRemoteAddr();
    }
}
