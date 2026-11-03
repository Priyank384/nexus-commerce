package com.example.nexusCommerce.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The editable half of an account. Email is deliberately absent: it is the login identity,
 * every order and address already points at it, and changing it needs its own verified
 * flow rather than a field on this form.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateProfileRequestDto {

    @NotBlank(message = "Name must not be blank")
    @Size(max = 120, message = "Name must be at most 120 characters")
    private String name;

    @Size(max = 20, message = "Phone must be at most 20 characters")
    private String phone;
}
