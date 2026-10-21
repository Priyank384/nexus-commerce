package com.example.nexusCommerce.dtos;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoginResponseDto {
    private String token;
    @Builder.Default
    private String tokenType = "Bearer";
    private Instant expiresAt;
    /** Opaque, single-use refresh token stored in Redis; use POST /api/v1/auth/refresh. */
    private String refreshToken;
    private GetUserResponseDto user;
}
