package com.example.nexusCommerce.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.nexusCommerce.exceptions.InvalidCredentialsException;
import com.example.nexusCommerce.schema.Role;

/**
 * The one place that decides who a shopper is. Every guest endpoint -- cart, checkout, payment,
 * idempotency -- reads through these two methods, so the properties below are the whole
 * isolation guarantee between anonymous browsers and signed-in accounts.
 */
class ShopperIdentityTest {

    private static final String TOKEN = OpaqueToken.mint();

    private static AuthenticatedUser caller() {
        return new AuthenticatedUser(1L, "shopper@example.com", Role.USER);
    }

    @Test
    void aSignedInCallerWinsEvenWhenItAlsoSendsAGuestHeader() {
        ShopperIdentity identity = ShopperIdentity.require(caller(), TOKEN);

        assertEquals(1L, identity.userId());
        assertNull(identity.guestToken(), "an account never acts as a guest");
        assertFalse(identity.isGuest());
    }

    @Test
    void aWellFormedHeaderOpensAGuestSession() {
        ShopperIdentity identity = ShopperIdentity.require(null, "  " + TOKEN + "  ");

        assertTrue(identity.isGuest());
        assertEquals(TOKEN, identity.guestToken());
    }

    @Test
    void noCredentialAtAllIsAnAuthenticationFailure() {
        assertThrows(InvalidCredentialsException.class, () -> ShopperIdentity.require(null, null));
        assertThrows(InvalidCredentialsException.class, () -> ShopperIdentity.require(null, "   "));
    }

    @Test
    void aForgedOrTruncatedTokenIsRejectedRatherThanTreatedAsASession() {
        for (String bad : new String[] {"guest", "admin", TOKEN + "x", TOKEN.substring(0, 42),
                // Anything outside base64url would be an injection attempt against the Redis
                // key below, where "::" is the field separator.
                TOKEN.substring(0, 40) + "a::", "user:1", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA!"}) {
            assertThrows(InvalidCredentialsException.class, () -> ShopperIdentity.guestSession(bad), bad);
        }
    }

    @Test
    void thePrincipalKeySeparatesAnAccountFromEveryGuestSession() {
        String member = new ShopperIdentity(1L, null).principalKey();
        String guest = new ShopperIdentity(null, TOKEN).principalKey();

        assertEquals("user:1", member);
        assertEquals("guest:" + TOKEN, guest);
        // A user id of 1 and a session that happens to start with "1" must not share a slot.
        assertNotEquals(member, new ShopperIdentity(null, "1" + "a".repeat(42)).principalKey());
    }

    @Test
    void twoBrowserSessionsNeverShareAPrincipal() {
        assertNotEquals(new ShopperIdentity(null, OpaqueToken.mint()).principalKey(),
                new ShopperIdentity(null, OpaqueToken.mint()).principalKey());
    }

    @Test
    void mintedTokensAreUrlSafeUniqueAndTheLengthTheClaimDtoAccepts() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String token = OpaqueToken.mint();
            assertEquals(43, token.length());
            assertTrue(token.matches("[A-Za-z0-9_-]{43}"), token);
            assertTrue(seen.add(token), "two mints collided: " + token);
        }
    }

    @Test
    void everyMintedTokenIsOneTheHeaderParserAccepts() {
        assertTrue(ShopperIdentity.guestSession(OpaqueToken.mint()).isPresent());
    }

    @Test
    void aBlankHeaderIsNoSessionRatherThanAnEmptyOne() {
        assertEquals(Optional.empty(), ShopperIdentity.guestSession(null));
        assertEquals(Optional.empty(), ShopperIdentity.guestSession(""));
    }

    @Test
    void theHeaderNameIsTheOneTheStorefrontSends() {
        assertEquals("X-Guest-Token", ShopperIdentity.HEADER);
    }
}
