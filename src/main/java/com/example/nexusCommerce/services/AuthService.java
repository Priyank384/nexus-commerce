package com.example.nexusCommerce.services;

import com.example.nexusCommerce.dtos.ChangePasswordRequestDto;
import com.example.nexusCommerce.dtos.LoginRequestDto;
import com.example.nexusCommerce.dtos.LoginResponseDto;
import com.example.nexusCommerce.dtos.ResetPasswordRequestDto;

public interface AuthService {
    LoginResponseDto login(LoginRequestDto requestDto);
    LoginResponseDto refresh(String refreshToken);
    /** Blacklists the access token's jti and drops the paired refresh token, if any. */
    void logout(String accessToken, String refreshToken);

    /**
     * Emails a single-use reset link to the address, if one is registered. Returns silently
     * either way: a non-answer for unknown addresses is what stops this endpoint turning
     * into an account-list oracle.
     */
    void forgotPassword(String email);

    void resetPassword(ResetPasswordRequestDto requestDto);

    /** Replaces a known password, and signs every other device out. */
    void changePassword(Long userId, ChangePasswordRequestDto requestDto);
}
