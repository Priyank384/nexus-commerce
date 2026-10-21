package com.example.nexusCommerce.security;

import com.example.nexusCommerce.schema.Role;

/**
 * The authenticated caller, resolved from a verified JWT and exposed to controllers via
 * {@code @AuthenticationPrincipal}. Identity comes from the token, never the request body.
 */
public record AuthenticatedUser(Long id, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
