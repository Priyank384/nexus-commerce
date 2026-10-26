package com.example.nexusCommerce.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.example.nexusCommerce.schema.Role;
import com.example.nexusCommerce.schema.User;

class JwtServiceTest {

    private static final String VALID_SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void rejectsSecretShorterThan256Bits() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new JwtService("too-short-secret", 60, new MockEnvironment()));

        assertTrue(error.getMessage().contains("JWT_SECRET"));
        assertTrue(error.getMessage().contains("32 bytes"));
    }

    @Test
    void rejectsTheBuiltInDevelopmentSecretInProd() {
        MockEnvironment prod = new MockEnvironment().withProperty("spring.profiles.active", "prod");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new JwtService("nexuscommerce-dev-only-secret-key-change-me-in-production-0123456789", 60, prod));

        assertTrue(error.getMessage().contains("development default"));
    }

    @Test
    void roundTripsCallerDetails() {
        JwtService service = new JwtService(VALID_SECRET, 60, new MockEnvironment());
        User user = User.builder().name("Ada").email("ada@example.com").role(Role.ADMIN).build();
        user.setId(42L);

        AuthenticatedUser caller = service.resolve(service.generateToken(user)).orElseThrow();

        assertEquals(42L, caller.id());
        assertEquals("ada@example.com", caller.email());
        assertEquals(Role.ADMIN, caller.role());
    }

    @Test
    void treatsTokenSignedWithAnotherKeyAsAnonymous() {
        String token = new JwtService(VALID_SECRET, 60, new MockEnvironment()).generateToken(
                User.builder().name("Ada").email("ada@example.com").role(Role.USER).build());

        JwtService other = new JwtService("fedcba9876543210fedcba9876543210", 60, new MockEnvironment());

        assertTrue(other.resolve(token).isEmpty());
    }
}
