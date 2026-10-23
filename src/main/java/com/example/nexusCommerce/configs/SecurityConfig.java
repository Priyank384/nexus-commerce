package com.example.nexusCommerce.configs;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.example.nexusCommerce.security.JwtAuthFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Stateless JWT security. Browsing the catalogue is public; placing orders and writing
 * reviews require a token; catalogue/user/order-management is ADMIN-only. Because Spring
 * Security is now on the classpath it would otherwise lock down every endpoint by default,
 * so this chain is what makes the app reachable.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:3001}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                // Uploaded product images: browser loads them without a bearer token.
                .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                // Liveness/readiness probes are checked by the platform (k8s, LB) without credentials.
                .requestMatchers("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                // All other actuator endpoints (metrics, prometheus, info) expose runtime detail.
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/products", "/api/v1/products/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/categories", "/api/v1/categories/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/reviews", "/api/v1/reviews/**").permitAll()
                // Razorpay's server-to-server callback carries no bearer token; it proves
                // itself with an HMAC over the raw body, so auth here is the signature check.
                .requestMatchers(HttpMethod.POST, "/api/v1/payments/razorpay/webhook").permitAll()
                // catalogue writes are admin-only
                .requestMatchers(HttpMethod.POST, "/api/v1/products", "/api/v1/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/products", "/api/v1/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/products", "/api/v1/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/categories", "/api/v1/categories/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/categories", "/api/v1/categories/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/categories", "/api/v1/categories/**").hasRole("ADMIN")
                // review moderation (delete) is admin; creating a review is any authenticated user
                .requestMatchers(HttpMethod.DELETE, "/api/v1/reviews", "/api/v1/reviews/**").hasRole("ADMIN")
                // admin aggregate endpoints
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                // order management
                .requestMatchers(HttpMethod.GET, "/api/v1/orders").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/orders", "/api/v1/orders/**").hasRole("ADMIN")
                // everything else (place order, write review, read own resources) needs a token
                .requestMatchers(HttpMethod.POST, "/api/v1/reviews", "/api/v1/reviews/**").authenticated()
                .requestMatchers("/api/v1/orders", "/api/v1/orders/**").authenticated()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                        writeError(response, 401, "Authentication required", "Unauthorized!"))
                .accessDeniedHandler((request, response, deniedException) ->
                        writeError(response, 403, "You do not have permission to perform this action", "Forbidden!"))
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeError(HttpServletResponse response, int status, String error, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"success\":false,\"message\":\"" + message + "\",\"error\":\"" + error + "\",\"data\":null}");
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Pinned rather than "*": the only browser origin that calls the API directly is the
        // storefront. Server-to-server BFF calls send no Origin and skip CORS entirely.
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
