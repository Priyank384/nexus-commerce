package com.example.nexusCommerce.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWishlistRequestDto {

    @NotBlank(message = "Wishlist name must not be blank")
    @Size(max = 120, message = "Wishlist name must be at most 120 characters")
    private String name;
}
