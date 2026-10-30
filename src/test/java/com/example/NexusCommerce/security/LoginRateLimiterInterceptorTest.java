package com.example.nexusCommerce.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The storefront reaches Spring through one BFF, so every shopper shares the same egress IP.
 * The limiter therefore has to ignore the traffic a browser generates on its own: successful
 * sign-ins and session refreshes. Only rejected sign-ins and registrations spend the bucket.
 */
class LoginRateLimiterInterceptorTest {

    private static final String IP = "203.0.113.7";
    private static final String KEY = "rate::login::" + IP;

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private LoginRateLimiterInterceptor interceptor;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redis);
        interceptor = new LoginRateLimiterInterceptor(provider);
        ReflectionTestUtils.setField(interceptor, "maxAttempts", 10);
        ReflectionTestUtils.setField(interceptor, "windowSeconds", 60);
    }

    @Test
    void aSuccessfulSignInDoesNotSpendTheBucket() throws Exception {
        when(values.get(KEY)).thenReturn(null);
        HttpServletRequest request = request("/api/v1/auth/login", "POST");

        assertTrue(interceptor.preHandle(request, response(200), new Object()));
        verify(values, never()).increment(anyString());

        interceptor.afterCompletion(request, response(200), new Object(), null);
        verify(values, never()).increment(anyString());
    }

    @Test
    void aRejectedSignInIsCounted() throws Exception {
        HttpServletRequest request = request("/api/v1/auth/login", "POST");

        interceptor.afterCompletion(request, response(401), new Object(), null);

        verify(values).increment(KEY);
    }

    @Test
    void signInIsRejectedOnceTheFailureWindowIsFull() throws Exception {
        when(values.get(KEY)).thenReturn("10");
        StringWriter body = new StringWriter();

        assertFalse(interceptor.preHandle(request("/api/v1/auth/login", "POST"), response(200, body), new Object()));
        assertTrue(body.toString().contains("Too many attempts"));
        verify(values, never()).increment(anyString());
    }

    @Test
    void refreshIsNeverLimitedBecauseItIsASessionOperation() throws Exception {
        HttpServletRequest request = request("/api/v1/auth/refresh", "POST");

        assertTrue(interceptor.preHandle(request, response(200), new Object()));
        interceptor.afterCompletion(request, response(401), new Object(), null);
        verifyNoInteractions(values);
    }

    @Test
    void everyRegistrationSpendsTheBucket() throws Exception {
        when(values.increment(KEY)).thenReturn(1L);

        assertTrue(interceptor.preHandle(request("/api/v1/users", "POST"), response(200), new Object()));
        verify(redis).expire(eq(KEY), any());

        when(values.increment(KEY)).thenReturn(11L);
        assertFalse(interceptor.preHandle(request("/api/v1/users", "POST"), response(200), new Object()));
    }

    @Test
    void aReadOfTheUserListIsNotARegistration() throws Exception {
        assertTrue(interceptor.preHandle(request("/api/v1/users", "GET"), response(200), new Object()));
        verifyNoInteractions(values);
    }

    @Test
    void aRedisBlipFailsOpen() throws Exception {
        when(values.get(anyString())).thenThrow(new QueryTimeoutException("timed out"));

        assertTrue(interceptor.preHandle(request("/api/v1/auth/login", "POST"), response(200), new Object()));
    }

    private static HttpServletRequest request(String uri, String method) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getMethod()).thenReturn(method);
        when(request.getRemoteAddr()).thenReturn(IP);
        return request;
    }

    private static HttpServletResponse response(int status) {
        return response(status, new StringWriter());
    }

    private static HttpServletResponse response(int status, StringWriter body) {
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getStatus()).thenReturn(status);
        try {
            when(response.getWriter()).thenReturn(new PrintWriter(body));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return response;
    }
}
