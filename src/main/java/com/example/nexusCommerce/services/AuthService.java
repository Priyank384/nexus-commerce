package com.example.nexusCommerce.services;

import com.example.nexusCommerce.dtos.LoginRequestDto;
import com.example.nexusCommerce.dtos.LoginResponseDto;

public interface AuthService {
    LoginResponseDto login(LoginRequestDto requestDto);
    LoginResponseDto refresh(String refreshToken);
    /** Blacklists the access token's jti and drops the paired refresh token, if any. */
    void logout(String accessToken, String refreshToken);
}
