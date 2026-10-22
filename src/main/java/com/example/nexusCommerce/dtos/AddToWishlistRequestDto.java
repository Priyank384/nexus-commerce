package com.example.nexusCommerce.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddToWishlistRequestDto {

    @NotNull(message = "productId must not be null")
    @Positive(message = "productId must be positive")
    private Long productId;

    /** Optional: saves a specific size+colour rather than the whole product. */
    @Positive(message = "variantId must be positive")
    private Long variantId;
}
