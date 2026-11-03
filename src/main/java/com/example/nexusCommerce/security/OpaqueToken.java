package com.example.nexusCommerce.security;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Unguessable, opaque secrets handed to callers that must prove something later without ever
 * being named in a database relationship: guest sessions, reset links, order claim links.
 *
 * <p>Random rather than derived (from an id, a timestamp or a HMAC of either) on purpose. A
 * derived value tells an attacker that a gap in the identifier space means an account or order
 * that does not exist, and these tokens are the only proof some of them have.
 */
public final class OpaqueToken {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int BYTES = 32;

    private OpaqueToken() {
    }

    /** 32 random bytes as base64url without padding: 43 characters, URL-safe in a query string. */
    public static String mint() {
        byte[] bytes = new byte[BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
