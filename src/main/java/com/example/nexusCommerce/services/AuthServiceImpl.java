package com.example.nexusCommerce.services;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.nexusCommerce.adapters.UserAdapter;
import com.example.nexusCommerce.dtos.LoginRequestDto;
import com.example.nexusCommerce.dtos.LoginResponseDto;
import com.example.nexusCommerce.exceptions.InvalidCredentialsException;
import com.example.nexusCommerce.repositories.UserRepository;
import com.example.nexusCommerce.schema.User;
import com.example.nexusCommerce.security.JwtService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserAdapter userAdapter;

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

        String token = jwtService.generateToken(user);
        return LoginResponseDto.builder()
                .token(token)
                .expiresAt(jwtService.expiresAt())
                .user(userAdapter.mapToGetUserResponseDto(user))
                .build();
    }
}
