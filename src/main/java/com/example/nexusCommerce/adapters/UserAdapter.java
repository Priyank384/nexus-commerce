package com.example.nexusCommerce.adapters;

import org.springframework.stereotype.Component;

import com.example.nexusCommerce.dtos.GetUserResponseDto;
import com.example.nexusCommerce.schema.User;

@Component
public class UserAdapter {

    public GetUserResponseDto mapToGetUserResponseDto(User user) {
        return GetUserResponseDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
