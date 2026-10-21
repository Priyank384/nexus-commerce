package com.example.nexusCommerce.controllers;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.example.nexusCommerce.dtos.GetUserResponseDto;
import com.example.nexusCommerce.dtos.LoginResponseDto;
import com.example.nexusCommerce.exceptions.InvalidCredentialsException;
import com.example.nexusCommerce.security.JwtAuthFilter;
import com.example.nexusCommerce.services.AuthService;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    @Test
    void login_returns200WithTokenAndUser() throws Exception {
        GetUserResponseDto user = GetUserResponseDto.builder()
                .id(1L)
                .name("Test User")
                .email("test@example.com")
                .build();
        LoginResponseDto response = LoginResponseDto.builder()
                .token("jwt-token")
                .expiresAt(Instant.parse("2026-01-01T00:00:00Z"))
                .user(user)
                .build();

        when(authService.login(any())).thenReturn(response);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"test@example.com\", \"password\": \"secret123\"}"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.token").value("jwt-token"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.user.email").value("test@example.com"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.user.passwordHash").doesNotExist());
    }

    @Test
    void login_returns401OnInvalidCredentials() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException("Invalid email or password!"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"test@example.com\", \"password\": \"wrong\"}"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("$.success").value(false));
    }
}
