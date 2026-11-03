package com.example.nexusCommerce.services;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.nexusCommerce.adapters.UserAdapter;
import com.example.nexusCommerce.dtos.ChangePasswordRequestDto;
import com.example.nexusCommerce.dtos.GetUserResponseDto;
import com.example.nexusCommerce.dtos.LoginRequestDto;
import com.example.nexusCommerce.dtos.LoginResponseDto;
import com.example.nexusCommerce.dtos.ResetPasswordRequestDto;
import com.example.nexusCommerce.exceptions.InvalidCredentialsException;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.RateLimitExceededException;
import com.example.nexusCommerce.repositories.UserRepository;
import com.example.nexusCommerce.schema.Role;
import com.example.nexusCommerce.schema.User;
import com.example.nexusCommerce.security.JwtService;
import com.example.nexusCommerce.security.LoginAttemptService;
import com.example.nexusCommerce.security.PasswordResetService;
import com.example.nexusCommerce.security.RefreshTokenService;
import com.example.nexusCommerce.security.TokenBlacklistService;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserAdapter userAdapter;

    @Mock
    private RefreshTokenService refreshTokens;

    @Mock
    private TokenBlacklistService blacklist;

    @Mock
    private LoginAttemptService loginAttempts;

    @Mock
    private PasswordResetService passwordResets;

    @Mock
    private NotificationService notifications;

    @InjectMocks
    private AuthServiceImpl authService;

    private static User user(boolean enabled) {
        User user = User.builder()
                .name("Test User")
                .email("test@example.com")
                .passwordHash("$2a$10$hashed")
                .role(Role.USER)
                .enabled(enabled)
                .build();
        user.setId(1L);
        return user;
    }

    @Test
    void login_issuesTokenOnValidCredentials() {
        LoginRequestDto dto = LoginRequestDto.builder()
                .email("test@example.com")
                .password("secret123")
                .build();
        User user = user(true);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "$2a$10$hashed")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");
        when(userAdapter.mapToGetUserResponseDto(user))
                .thenReturn(GetUserResponseDto.builder().id(1L).email("test@example.com").build());

        LoginResponseDto result = authService.login(dto);

        assertEquals("jwt-token", result.getToken());
        assertEquals("Bearer", result.getTokenType());
        assertNotNull(result.getUser());
        assertEquals(1L, result.getUser().getId());
    }

    @Test
    void login_rejectsWrongPassword() {
        LoginRequestDto dto = LoginRequestDto.builder()
                .email("test@example.com")
                .password("wrong")
                .build();
        User user = user(true);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$2a$10$hashed")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(dto));
        verify(jwtService, org.mockito.Mockito.never()).generateToken(any());
        verify(loginAttempts).recordFailure("test@example.com");
    }

    @Test
    void login_isRejectedWhileTheAccountIsRateLimited() {
        LoginRequestDto dto = LoginRequestDto.builder()
                .email("test@example.com")
                .password("secret123")
                .build();
        when(loginAttempts.isBlocked("test@example.com")).thenReturn(true);

        assertThrows(RateLimitExceededException.class, () -> authService.login(dto));
        verify(userRepository, org.mockito.Mockito.never()).findByEmail(any());
    }

    @Test
    void login_rejectsUnknownEmail() {
        LoginRequestDto dto = LoginRequestDto.builder()
                .email("nobody@example.com")
                .password("secret123")
                .build();

        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.login(dto));
    }

    @Test
    void login_rejectsDisabledAccount() {
        LoginRequestDto dto = LoginRequestDto.builder()
                .email("test@example.com")
                .password("secret123")
                .build();
        User user = user(false);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "$2a$10$hashed")).thenReturn(true);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(dto));
    }

    @Test
    void login_looksUpEmailLowercased() {
        LoginRequestDto dto = LoginRequestDto.builder()
                .email("  Test@Example.COM ")
                .password("secret123")
                .build();
        User user = user(true);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "$2a$10$hashed")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");
        when(userAdapter.mapToGetUserResponseDto(user))
                .thenReturn(GetUserResponseDto.builder().id(1L).build());

        assertEquals("jwt-token", authService.login(dto).getToken());
    }

    @Test
    void forgotPassword_doesNotMailAnAddressWithNoAccount() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        authService.forgotPassword("nobody@example.com");

        verify(passwordResets, never()).issue(any());
        verify(notifications, never()).sendPasswordReset(any(), any());
    }

    @Test
    void forgotPassword_doesNotMailADisabledAccount() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user(false)));

        authService.forgotPassword("test@example.com");

        verify(notifications, never()).sendPasswordReset(any(), any());
    }

    @Test
    void forgotPassword_emailsTheTokenToTheAddressOnFile() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user(true)));
        when(passwordResets.issue(1L)).thenReturn("tok-1");

        authService.forgotPassword("  Test@Example.COM ");

        verify(notifications).sendPasswordReset("test@example.com", "tok-1");
    }

    @Test
    void forgotPassword_staysSilentInsideTheSendCooldown() {
        // issue() returning null is the cooldown answer; the earlier link is still live,
        // so a second mail would only train spammers to burn through the window.
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user(true)));
        when(passwordResets.issue(1L)).thenReturn(null);

        authService.forgotPassword("test@example.com");

        verify(notifications, never()).sendPasswordReset(any(), any());
    }

    @Test
    void resetPassword_storesTheNewHashAndEndsEveryOtherSession() {
        User user = user(true);
        when(passwordResets.consume("tok-1")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("brand-new-1")).thenReturn("$2a$10$fresh");

        authService.resetPassword(new ResetPasswordRequestDto("tok-1", "brand-new-1"));

        assertEquals("$2a$10$fresh", user.getPasswordHash());
        verify(refreshTokens).revokeAllForUser(1L);
    }

    @Test
    void resetPassword_rejectsAnUnknownOrSpentToken() {
        when(passwordResets.consume("gone")).thenReturn(null);

        assertThrows(InvalidRequestException.class,
                () -> authService.resetPassword(new ResetPasswordRequestDto("gone", "brand-new-1")));

        verify(userRepository, never()).save(any());
        verify(refreshTokens, never()).revokeAllForUser(any());
    }

    @Test
    void resetPassword_doesNotBurnTheLinkOnAnUnusablePassword() {
        assertThrows(InvalidRequestException.class, () -> authService.resetPassword(
                new ResetPasswordRequestDto("tok-1", "x".repeat(73))));

        // The shopper can fix the password and resubmit the same link.
        verify(passwordResets, never()).consume(any());
    }

    @Test
    void changePassword_requiresTheCurrentOne() {
        User user = user(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-pass-1", "$2a$10$hashed")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.changePassword(
                1L, new ChangePasswordRequestDto("wrong-pass-1", "brand-new-1")));

        assertEquals("$2a$10$hashed", user.getPasswordHash());
        verify(refreshTokens, never()).revokeAllForUser(any());
    }

    @Test
    void changePassword_rotatesTheHashAndRevokesEveryRefreshToken() {
        User user = user(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "$2a$10$hashed")).thenReturn(true);
        when(passwordEncoder.encode("brand-new-1")).thenReturn("$2a$10$fresh");

        authService.changePassword(1L, new ChangePasswordRequestDto("secret123", "brand-new-1"));

        assertEquals("$2a$10$fresh", user.getPasswordHash());
        verify(refreshTokens).revokeAllForUser(1L);
    }
}
