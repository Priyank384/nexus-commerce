package com.example.nexusCommerce.services;

import com.example.nexusCommerce.dtos.CreateUserRequestDto;
import com.example.nexusCommerce.dtos.GetUserResponseDto;
import com.example.nexusCommerce.dtos.UpdateProfileRequestDto;

public interface UserService {
    GetUserResponseDto createUser(CreateUserRequestDto requestDto);

    GetUserResponseDto getProfile(Long userId);

    GetUserResponseDto updateProfile(Long userId, UpdateProfileRequestDto requestDto);
}
