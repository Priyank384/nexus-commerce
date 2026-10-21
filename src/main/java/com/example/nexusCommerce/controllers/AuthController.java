package com.example.nexusCommerce.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.nexusCommerce.dtos.LoginRequestDto;
import com.example.nexusCommerce.dtos.LoginResponseDto;
import com.example.nexusCommerce.dtos.RefreshRequestDto;
import com.example.nexusCommerce.services.AuthService;
import com.example.nexusCommerce.utils.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Auth", description = "Registration and login; issues the JWT bearer token")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    @Operation(summary = "Log in",
               description = "Exchange email + password for a short-lived JWT bearer token and a single-use refresh token.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseDto>> login(@Valid @RequestBody LoginRequestDto requestDto) {
        LoginResponseDto response = authService.login(requestDto);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful!"));
    }

    @Operation(summary = "Refresh the access token",
               description = "Consumes the previous refresh token (single-use) and returns a new access+refresh pair.")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponseDto>> refresh(@Valid @RequestBody RefreshRequestDto requestDto) {
        LoginResponseDto response = authService.refresh(requestDto.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed"));
    }

    @Operation(summary = "Log out",
               description = "Blacklists the current access token's jti in Redis and consumes the paired refresh token.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody RefreshRequestDto requestDto) {
        String access = (authorization != null && authorization.startsWith(BEARER_PREFIX))
                ? authorization.substring(BEARER_PREFIX.length())
                : null;
        authService.logout(access, requestDto.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(null, "Signed out"));
    }
}
