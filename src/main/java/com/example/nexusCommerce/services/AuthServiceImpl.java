package com.example.nexusCommerce.services;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.nexusCommerce.adapters.UserAdapter;
import com.example.nexusCommerce.dtos.LoginRequestDto;
import com.example.nexusCommerce.dtos.LoginResponseDto;
import com.example.nexusCommerce.exceptions.InvalidCredentialsException;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.repositories.UserRepository;
import com.example.nexusCommerce.schema.User;
import com.example.nexusCommerce.security.JwtService;
import com.example.nexusCommerce.security.RefreshTokenService;
import com.example.nexusCommerce.security.TokenBlacklistService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserAdapter userAdapter;
    private final RefreshTokenService refreshTokens;
    private final TokenBlacklistService blacklist;

    @Transactional(readOnly = true)
    @Override
    public LoginResponseDto login(LoginRequestDto requestDto) {
        String email = requestDto.getEmail() == null ? null : requestDto.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password!"));
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(requestDto.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password!");
        }
        if (!user.isEnabled()) {
            throw new InvalidCredentialsException("This account has been disabled.");
        }
        return issuePair(user);
    }

    @Transactional(readOnly = true)
    @Override
    public LoginResponseDto refresh(String refreshToken) {
        Long uid = refreshTokens.consume(refreshToken);
        if (uid == null) {
            throw new InvalidCredentialsException("Refresh token is invalid or already used.");
        }
        User user = userRepository.findById(uid)
                .orElseThrow(() -> new InvalidCredentialsException("Refresh token is invalid or already used."));
        if (!user.isEnabled()) {
            throw new InvalidCredentialsException("This account has been disabled.");
        }
        return issuePair(user);
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            JwtService.TokenMetadata meta = jwtService.inspect(accessToken);
            if (meta != null && meta.expiresAt() != null) {
                blacklist.revoke(meta.jti(), meta.expiresAt());
            }
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokens.revoke(refreshToken);
        } else if (refreshToken == null) {
            throw new InvalidRequestException("refreshToken is required to sign out fully");
        }
    }

    private LoginResponseDto issuePair(User user) {
        String token = jwtService.generateToken(user);
        return LoginResponseDto.builder()
                .token(token)
                .expiresAt(jwtService.expiresAt())
                .refreshToken(refreshTokens.issue(user.getId()))
                .user(userAdapter.mapToGetUserResponseDto(user))
                .build();
    }
}
