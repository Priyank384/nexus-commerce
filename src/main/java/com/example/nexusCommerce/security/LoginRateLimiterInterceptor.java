package com.example.nexusCommerce.security;

import java.io.IOException;
import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Fixed-window rate limiter keyed on the caller's IP, applied only to POST
 * /api/v1/auth/login. Enough to slow credential stuffing without penalising
 * shared NAT households; for real hardening move to a gateway or WAF.
 *
 * Redis access is resolved lazily via ObjectProvider: the interceptor bean
 * itself must always exist (otherwise web-slice tests without Redis fail
 * to wire the chain), but preHandle is a no-op when no StringRedisTemplate
 * is registered at runtime.
 */
@Component
@RequiredArgsConstructor
public class LoginRateLimiterInterceptor implements HandlerInterceptor {

    private static final String PREFIX = "rate::login::";

    private final ObjectProvider<StringRedisTemplate> redisProvider;

    @Value("${app.rate-limit.login.max:10}")
    private int maxAttempts;

    @Value("${app.rate-limit.login.window-seconds:60}")
    private int windowSeconds;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) return true;

        String ip = clientIp(request);
        String key = PREFIX + ip;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofSeconds(windowSeconds));
        }
        if (count != null && count > maxAttempts) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"success\":false,\"message\":\"Too many login attempts\",\"error\":\"Rate limit exceeded\",\"data\":null}");
            return false;
        }
        return true;
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
