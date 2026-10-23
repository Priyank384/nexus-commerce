package com.example.nexusCommerce.services;

import com.example.nexusCommerce.dtos.AdminMetricsResponseDto;
import com.example.nexusCommerce.dtos.GetUserResponseDto;
import com.example.nexusCommerce.dtos.UpdateUserRequestDto;
import com.example.nexusCommerce.utils.PageResponse;

public interface AdminService {
    AdminMetricsResponseDto getMetrics();
    PageResponse<GetUserResponseDto> listUsers(int page, int size, String q);
    GetUserResponseDto getUser(Long id);
    GetUserResponseDto updateUser(Long id, UpdateUserRequestDto requestDto);
}
