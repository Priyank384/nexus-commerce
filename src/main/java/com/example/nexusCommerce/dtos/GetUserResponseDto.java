package com.example.nexusCommerce.dtos;

import java.math.BigDecimal;

import com.example.nexusCommerce.schema.Role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GetUserResponseDto {
    private Long id;
    private String name;
    private String email;
    private String phone;
    private Role role;
    private boolean enabled;
    private java.time.LocalDateTime createdAt;
}
