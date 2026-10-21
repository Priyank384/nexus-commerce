package com.example.nexusCommerce.security;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    // Optional so web-only test slices without Redis autoconfig still boot; in those
    // contexts nothing was ever revoked, so skipping the check is correct.
    private final ObjectProvider<TokenBlacklistService> blacklistProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length());
            TokenBlacklistService blacklist = blacklistProvider.getIfAvailable();
            JwtService.TokenMetadata meta = jwtService.inspect(token);
            boolean revoked = meta != null && blacklist != null && blacklist.isRevoked(meta.jti());
            if (meta != null && !revoked) {
                jwtService.resolve(token).ifPresent(user -> {
                    var authority = new SimpleGrantedAuthority("ROLE_" + user.role().name());
                    var auth = new UsernamePasswordAuthenticationToken(user, null, List.of(authority));
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });
            }
        }
        filterChain.doFilter(request, response);
    }
}
