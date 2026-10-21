package com.example.nexusCommerce.services;

import com.example.nexusCommerce.dtos.CreateUserRequestDto;
import com.example.nexusCommerce.dtos.GetUserResponseDto;

public interface UserService {
    GetUserResponseDto createUser(CreateUserRequestDto requestDto);
}
