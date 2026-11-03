package com.example.nexusCommerce.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * The replay slot is the one place a guest credential ends up inside a Redis key, so these
 * tests pin the key shape rather than the JSON round-trip: two anonymous browsers retrying the
 * same guessed key must not read each other's placed order.
 */
class IdempotencyServiceTest {

    @SuppressWarnings("unchecked")
    private StringRedisTemplate acceptingTemplate() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        doReturn(values).when(redis).opsForValue();
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        return redis;
    }

    private String keyFor(ShopperIdentity caller) {
        StringRedisTemplate redis = acceptingTemplate();
        IdempotencyService service = new IdempotencyService(redis);

        service.tryStart(caller, "checkout", "abc-123");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(redis.opsForValue()).setIfAbsent(captor.capture(), anyString(), any(Duration.class));
        return captor.getValue();
    }

    @Test
    void anAccountScopesTheKeyByItsUserId() {
        assertEquals("idem::user:1::checkout::abc-123", keyFor(new ShopperIdentity(1L, null)));
    }

    @Test
    void aGuestSessionScopesTheKeyByItsOwnToken() {
        String token = OpaqueToken.mint();

        assertEquals("idem::guest:" + token + "::checkout::abc-123",
                keyFor(new ShopperIdentity(null, token)));
    }

    @Test
    void theSameGuessedKeyUnderTwoIdentitiesCannotCollide() {
        String first = keyFor(new ShopperIdentity(null, OpaqueToken.mint()));
        String second = keyFor(new ShopperIdentity(null, OpaqueToken.mint()));
        String member = keyFor(new ShopperIdentity(1L, null));

        assertTrue(first.startsWith("idem::guest:") && second.startsWith("idem::guest:"));
        assertTrue(!first.equals(second) && !first.equals(member) && !second.equals(member));
    }
}
