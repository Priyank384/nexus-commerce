package com.example.nexusCommerce.services;

import java.nio.charset.StandardCharsets;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.nexusCommerce.adapters.UserAdapter;
import com.example.nexusCommerce.dtos.ChangePasswordRequestDto;
import com.example.nexusCommerce.dtos.LoginRequestDto;
import com.example.nexusCommerce.dtos.LoginResponseDto;
import com.example.nexusCommerce.dtos.ResetPasswordRequestDto;
import com.example.nexusCommerce.exceptions.InvalidCredentialsException;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.RateLimitExceededException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.UserRepository;
import com.example.nexusCommerce.schema.User;
import com.example.nexusCommerce.security.JwtService;
import com.example.nexusCommerce.security.LoginAttemptService;
import com.example.nexusCommerce.security.PasswordResetService;
import com.example.nexusCommerce.security.RefreshTokenService;
import com.example.nexusCommerce.security.TokenBlacklistService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserAdapter userAdapter;
    private final RefreshTokenService refreshTokens;
    private final TokenBlacklistService blacklist;
    private final LoginAttemptService loginAttempts;
    private final PasswordResetService passwordResets;
    private final NotificationService notifications;

    @Transactional(readOnly = true)
    @Override
    public LoginResponseDto login(LoginRequestDto requestDto) {
        String email = normalizeEmail(requestDto.getEmail());
        if (loginAttempts.isBlocked(email)) {
            throw new RateLimitExceededException(
                    "Too many failed sign-in attempts for this account; try again later");
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(requestDto.getPassword(), user.getPasswordHash())) {
            loginAttempts.recordFailure(email);
            throw new InvalidCredentialsException("Invalid email or password!");
        }
        if (!user.isEnabled()) {
            throw new InvalidCredentialsException("This account has been disabled.");
        }
        loginAttempts.clear(email);
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

    @Transactional(readOnly = true)
    @Override
    public void forgotPassword(String email) {
        String normalized = normalizeEmail(email);
        User account = userRepository.findByEmail(normalized).orElse(null);
        if (account == null) {
            // Same outcome as a real one, and the same response body, so timing and content
            // both refuse to confirm whether the address is registered.
            log.info("Password reset requested for an address with no account");
            return;
        }
        if (!account.isEnabled()) {
            log.info("Password reset requested for disabled account {}", account.getId());
            return;
        }
        String token = passwordResets.issue(account.getId());
        if (token == null) {
            return;
        }
        notifications.sendPasswordReset(account.getEmail(), token);
    }

    @Transactional
    @Override
    public void resetPassword(ResetPasswordRequestDto requestDto) {
        // Checked before the token is spent: burning a single-use link on an over-long
        // password would send the shopper back through the whole forgot-password flow.
        requireFeasiblePassword(requestDto.getPassword());
        Long userId = passwordResets.consume(requestDto.getToken());
        User account = userId == null ? null : userRepository.findById(userId).orElse(null);
        if (account == null) {
            throw new InvalidRequestException(
                    "This reset link is invalid, has expired, or has already been used.");
        }
        storePassword(account, requestDto.getPassword());
        refreshTokens.revokeAllForUser(account.getId());
    }

    @Transactional
    @Override
    public void changePassword(Long userId, ChangePasswordRequestDto requestDto) {
        User account = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));
        if (account.getPasswordHash() == null
                || !passwordEncoder.matches(requestDto.getCurrentPassword(), account.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is incorrect.");
        }
        requireFeasiblePassword(requestDto.getNewPassword());
        storePassword(account, requestDto.getNewPassword());
        // Every session dies, including this one, which is why the storefront signs the
        // shopper straight back in with the password it just proved it knows.
        refreshTokens.revokeAllForUser(account.getId());
    }

    /**
     * Mirrors the signup guard: BCrypt truncates past 72 bytes and rejects longer input, which
     * would otherwise surface as a 500 rather than a message the shopper can act on.
     */
    private static void requireFeasiblePassword(String raw) {
        if (raw.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidRequestException("Password must be at most 72 bytes");
        }
    }

    private void storePassword(User account, String raw) {
        account.setPasswordHash(passwordEncoder.encode(raw));
        userRepository.save(account);
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
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
