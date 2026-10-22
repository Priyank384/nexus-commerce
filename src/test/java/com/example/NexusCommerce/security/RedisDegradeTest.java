package com.example.nexusCommerce.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * These two controls are the reason a Redis blip used to answer 500 on authenticated reads and
 * on sign-in. Both are best-effort by design, so both have to degrade to "allow" rather than to
 * an outage -- and the tests pin that down, because the failure mode is invisible until cache
 * latency actually spikes.
 */
class RedisDegradeTest {

    private static final QueryTimeoutException BLIP = new QueryTimeoutException("timed out");

    @SuppressWarnings("unchecked")
    private StringRedisTemplate templateWhoseReadsFail() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        // doThrow rather than when().thenThrow(): RedisTemplate's own methods are not
        // overridable in the way a stubbed call expects, and the do- form applies to both.
        doReturn(values).when(redis).opsForValue();
        doThrow(BLIP).when(values).increment(anyString());
        doThrow(BLIP).when(values).set(anyString(), anyString(), any(Duration.class));
        doThrow(BLIP).when(redis).hasKey(anyString());
        return redis;
    }

    @Test
    void unreadableRevocationListDoesNotRejectAnActiveToken() {
        TokenBlacklistService blacklist = new TokenBlacklistService(templateWhoseReadsFail());

        assertFalse(blacklist.isRevoked("jti-123"));
    }

    @Test
    void logoutStillSucceedsWhenTheRevocationCannotBeRecorded() {
        TokenBlacklistService blacklist = new TokenBlacklistService(templateWhoseReadsFail());

        assertDoesNotThrow(() -> blacklist.revoke("jti-123", Instant.now().plusSeconds(900)));
    }

    @Test
    void aNullJtiIsNeverTreatedAsRevoked() {
        TokenBlacklistService blacklist = new TokenBlacklistService(mock(StringRedisTemplate.class));

        assertFalse(blacklist.isRevoked(null));
    }

    @Test
    void loginIsAllowedWhenTheRateCounterIsUnreadable() throws Exception {
        // Build the failing template before opening the stub: creating and stubbing other
        // mocks inside thenReturn(...) leaves Mockito mid-stubbing.
        StringRedisTemplate broken = templateWhoseReadsFail();
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> provider =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(broken);
        LoginRateLimiterInterceptor limiter = new LoginRateLimiterInterceptor(provider);
        jakarta.servlet.http.HttpServletRequest request = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("203.0.113.7");

        assertTrue(limiter.preHandle(request, mock(jakarta.servlet.http.HttpServletResponse.class), new Object()));
    }
}
