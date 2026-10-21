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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.nexusCommerce.adapters.UserAdapter;
import com.example.nexusCommerce.dtos.GetUserResponseDto;
import com.example.nexusCommerce.dtos.LoginRequestDto;
import com.example.nexusCommerce.dtos.LoginResponseDto;
import com.example.nexusCommerce.exceptions.InvalidCredentialsException;
import com.example.nexusCommerce.repositories.UserRepository;
import com.example.nexusCommerce.schema.Role;
import com.example.nexusCommerce.schema.User;
import com.example.nexusCommerce.security.JwtService;

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
}
