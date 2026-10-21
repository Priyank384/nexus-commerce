package com.example.nexusCommerce.services;

import com.example.nexusCommerce.dtos.LoginRequestDto;
import com.example.nexusCommerce.dtos.LoginResponseDto;

public interface AuthService {
    LoginResponseDto login(LoginRequestDto requestDto);
}
