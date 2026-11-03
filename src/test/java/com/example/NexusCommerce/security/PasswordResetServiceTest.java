package com.example.nexusCommerce.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * A reset token is a live credential that can only be judged by what happens to the store: one
 * link per account, spent on first use, and nothing left behind for the rest of the TTL.
 */
class PasswordResetServiceTest {

    private static final long USER_ID = 105L;
    private static final String INDEX_KEY = "pwreset-user::" + USER_ID;

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private PasswordResetService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        service = new PasswordResetService(redis);
        ReflectionTestUtils.setField(service, "ttlMinutes", 15L);
        ReflectionTestUtils.setField(service, "cooldownSeconds", 60L);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
    }

    @Test
    void aIssuedLinkIsBoundToTheAccountAndGivenTheFullWindow() {
        String token = service.issue(USER_ID);

        assertEquals(43, token.length());
        verify(values).set(eq("pwreset::" + token), eq(String.valueOf(USER_ID)), eq(Duration.ofMinutes(15)));
        verify(values).set(INDEX_KEY, token, Duration.ofMinutes(15));
    }

    @Test
    void askingForASecondLinkRetiresTheFirst() {
        String first = service.issue(USER_ID);
        // The store still holds the earlier link, so the account's newest request has to delete it
        // rather than leave two live credentials for the same password.
        when(values.getAndDelete(INDEX_KEY)).thenReturn(first);

        String second = service.issue(USER_ID);

        verify(redis).delete("pwreset::" + first);
        verify(values).set(eq("pwreset::" + second), anyString(), any(Duration.class));
    }

    @Test
    void aRequestInsideTheCooldownSendsNothingAndKeepsTheWorkingLink() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertNull(service.issue(USER_ID));
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
        verify(values, never()).getAndDelete(INDEX_KEY);
    }

    @Test
    void aTokenWorksOnceAndClearsTheIndexItWasFoundThrough() {
        String token = service.issue(USER_ID);
        when(values.getAndDelete("pwreset::" + token)).thenReturn(String.valueOf(USER_ID));
        when(values.get(INDEX_KEY)).thenReturn(token);

        assertEquals(USER_ID, service.consume(token));

        verify(redis).delete(INDEX_KEY);
    }

    @Test
    void usingAnOldLinkDoesNotEraseTheNewerOne() {
        String oldToken = "a".repeat(43);
        when(values.getAndDelete("pwreset::" + oldToken)).thenReturn(String.valueOf(USER_ID));
        when(values.get(INDEX_KEY)).thenReturn("b".repeat(43));

        assertEquals(USER_ID, service.consume(oldToken));

        verify(redis, never()).delete(INDEX_KEY);
    }

    @Test
    void aForgedOrExpiredTokenResolvesToNoAccount() {
        when(values.getAndDelete("pwreset::" + "z".repeat(43))).thenReturn(null);

        assertNull(service.consume("z".repeat(43)));
        assertNull(service.consume("  "));
        verify(redis, never()).delete(anyString());
    }
}
