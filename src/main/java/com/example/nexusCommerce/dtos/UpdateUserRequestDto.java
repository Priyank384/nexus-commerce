package com.example.nexusCommerce.dtos;

import com.example.nexusCommerce.schema.Role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateUserRequestDto {
    private Role role;
    private Boolean enabled;
}
