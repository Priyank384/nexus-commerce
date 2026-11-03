package com.example.nexusCommerce.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@SuppressWarnings("unchecked")
class TokenServiceTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final SetOperations<String, String> sets = mock(SetOperations.class);

    TokenServiceTest() {
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForSet()).thenReturn(sets);
    }

    private PasswordResetService resets() {
        PasswordResetService service = new PasswordResetService(redis);
        ReflectionTestUtils.setField(service, "ttlMinutes", 15L);
        ReflectionTestUtils.setField(service, "cooldownSeconds", 60L);
        return service;
    }

    private RefreshTokenService refresh() {
        RefreshTokenService service = new RefreshTokenService(redis);
        ReflectionTestUtils.setField(service, "ttlDays", 7L);
        return service;
    }

    @Test
    void resetTokenIsStoredAgainstTheUserIdAndSingleUse() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        String token = resets().issue(42L);

        assertNotNull(token);
        assertEquals(43, token.length(), "base64url of 32 bytes, unpadded");
        verify(values).set(eq("pwreset::" + token), eq("42"), eq(Duration.ofMinutes(15)));

        // The first consumer gets the id; a replay of the same link gets nothing.
        when(values.getAndDelete("pwreset::" + token)).thenReturn("42", (String) null);
        assertEquals(42L, resets().consume(token));
        assertNull(resets().consume(token));
    }

    @Test
    void aSecondResetRequestInsideTheCooldownIssuesNothing() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertNull(resets().issue(42L));

        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void aBlankResetTokenIsRejectedWithoutTouchingRedis() {
        assertNull(resets().consume("  "));

        verify(values, never()).getAndDelete(anyString());
    }

    @Test
    void refreshTokenIsIndexedPerUserSoAnAccountCanBeSignedOutAtOnce() {
        RefreshTokenService service = refresh();

        String token = service.issue(42L);

        verify(values).set(eq("refresh::" + token), eq("42"), eq(Duration.ofDays(7)));
        verify(sets).add("refresh-user::42", token);
        verify(redis).expire("refresh-user::42", Duration.ofDays(7));

        when(values.getAndDelete("refresh::" + token)).thenReturn("42");
        assertEquals(42L, service.consume(token));
        verify(sets).remove("refresh-user::42", token);
    }

    @Test
    void revokingAnAccountDeletesItsTokensAndTheIndex() {
        when(sets.members("refresh-user::42")).thenReturn(Set.of("t1", "t2"));
        RefreshTokenService service = refresh();

        service.revokeAllForUser(42L);

        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redis).delete(keys.capture());
        assertTrue(keys.getValue().containsAll(List.of("refresh::t1", "refresh::t2")));
        verify(redis).delete("refresh-user::42");
    }

    @Test
    void revokingAnAccountWithNoSessionsOnlyClearsTheIndex() {
        when(sets.members("refresh-user::42")).thenReturn(Set.of());

        refresh().revokeAllForUser(42L);

        verify(redis, never()).delete(any(Collection.class));
        verify(redis).delete("refresh-user::42");
    }

    @Test
    void logoutRemovesTheTokenFromItsUsersIndex() {
        when(values.get("refresh::t1")).thenReturn("42");

        refresh().revoke("t1");

        verify(redis).delete("refresh::t1");
        verify(sets).remove("refresh-user::42", "t1");
    }
}
