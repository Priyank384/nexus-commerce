package com.example.nexusCommerce.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

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
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = Duration.ofMinutes(expirationMinutes);
    }

    public Instant expiresAt() {
        return Instant.now().plus(expiration);
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
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
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long uid = claims.get("uid", Number.class).longValue();
            Role role = Role.valueOf(claims.get("role", String.class));
            return java.util.Optional.of(new AuthenticatedUser(uid, claims.getSubject(), role));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            return java.util.Optional.empty();
        }
    }
}
