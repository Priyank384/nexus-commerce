package com.example.nexusCommerce.services;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.nexusCommerce.adapters.UserAdapter;
import com.example.nexusCommerce.dtos.CreateUserRequestDto;
import com.example.nexusCommerce.dtos.GetUserResponseDto;
import com.example.nexusCommerce.exceptions.DuplicateResourceException;
import com.example.nexusCommerce.repositories.UserRepository;
import com.example.nexusCommerce.schema.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserAdapter userAdapter;

    @Transactional
    @Override
    public GetUserResponseDto createUser(CreateUserRequestDto requestDto) {
        String email = normalizeEmail(requestDto.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("User with email '" + requestDto.getEmail() + "' already exists!");
        }
        User user = User.builder()
                .name(requestDto.getName())
                .email(email)
                .phone(requestDto.getPhone())
                .passwordHash(passwordEncoder.encode(requestDto.getPassword()))
                .build();
        return userAdapter.mapToGetUserResponseDto(userRepository.save(user));
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
