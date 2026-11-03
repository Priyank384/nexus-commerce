package com.example.nexusCommerce.security;

import java.util.Optional;
import java.util.regex.Pattern;

import com.example.nexusCommerce.exceptions.InvalidCredentialsException;

/**
 * Whose cart, checkout or payment a request is acting on: an authenticated account, or the
 * anonymous browser session that has carried an {@value #HEADER} header since before the
 * shopper had any reason to sign in.
 *
 * <p>Both halves are never populated at once. A JWT wins over the header because an account is
 * the stronger claim on the same shopper, and {@code POST /api/v1/cart/merge} exists to move
 * guest lines onto the account rather than let a stale header shadow it.
 */
public record ShopperIdentity(Long userId, String guestToken) {

    public static final String HEADER = "X-Guest-Token";

    /** What {@link OpaqueToken#mint()} produces: 43 base64url characters, and nothing else. */
    private static final Pattern MINTED = Pattern.compile("[A-Za-z0-9_-]{43}");

    /**
     * Resolves the caller for an endpoint that either identity may use. The 401 is thrown here
     * rather than left to Spring Security because these endpoints are deliberately reachable
     * without a token: authorization is ownership of a cart or an order, and there is nothing
     * to be the owner of when neither identity is present.
     *
     * <p>The header is validated instead of trusted because it becomes part of a Redis key and
     * of a database lookup. Free-form text there would let one session's token be written to
     * name another principal's slot.
     */
    public static ShopperIdentity require(AuthenticatedUser caller, String guestTokenHeader) {
        if (caller != null) {
            return new ShopperIdentity(caller.id(), null);
        }
        String token = guestSession(guestTokenHeader).orElseThrow(() -> new InvalidCredentialsException(
                "Sign in, or send an " + HEADER + " header to check out as a guest"));
        return new ShopperIdentity(null, token);
    }

    /**
     * The session a header names, empty when none was sent. Endpoints where an anonymous caller
     * legitimately has nothing to identify use this instead of {@link #require}: a missing guest
     * header is a no-op there, while a malformed one is still refused.
     */
    public static Optional<String> guestSession(String guestTokenHeader) {
        if (guestTokenHeader == null || guestTokenHeader.isBlank()) {
            return Optional.empty();
        }
        String token = guestTokenHeader.trim();
        if (!MINTED.matcher(token).matches()) {
            throw new InvalidCredentialsException("That " + HEADER + " is not one this store issued");
        }
        return Optional.of(token);
    }

    public boolean isGuest() {
        return userId == null;
    }

    /** The principal an idempotency key is scoped to, so two shoppers cannot share a replay slot. */
    public String principalKey() {
        return userId != null ? "user:" + userId : "guest:" + guestToken;
    }
}
