package com.example.nexusCommerce.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.nexusCommerce.schema.Role;
import com.example.nexusCommerce.schema.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        // HMAC-SHA256 needs a 256-bit key. Without this guard the failure surfaces as a
        // WeakKeyException from inside JJWT, which does not name the variable to fix.
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least 32 bytes (found " + keyBytes.length
                            + "). Generate one with: openssl rand -base64 48");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expiration = Duration.ofMinutes(expirationMinutes);
    }

    public Instant expiresAt() {
        return Instant.now().plus(expiration);
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getEmail())
                .claim("uid", user.getId())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    /**
     * Returns the caller described by the token, or empty for any invalid/expired/unsigned
     * token. The filter treats empty as "unauthenticated" and simply does not populate the
     * security context; authorization then rejects the request.
     */
    public java.util.Optional<AuthenticatedUser> resolve(String token) {
        Claims claims = parseClaims(token);
        if (claims == null) return java.util.Optional.empty();
        try {
            Long uid = claims.get("uid", Number.class).longValue();
            Role role = Role.valueOf(claims.get("role", String.class));
            return java.util.Optional.of(new AuthenticatedUser(uid, claims.getSubject(), role));
        } catch (RuntimeException e) {
            return java.util.Optional.empty();
        }
    }

    /**
     * The jti + exp needed to add the token to the revocation blacklist on logout.
     * Returns null for any malformed or expired token (nothing to revoke).
     */
    public TokenMetadata inspect(String token) {
        Claims claims = parseClaims(token);
        if (claims == null) return null;
        Date exp = claims.getExpiration();
        return new TokenMetadata(claims.getId(), exp == null ? null : exp.toInstant());
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    public record TokenMetadata(String jti, Instant expiresAt) {}
}
