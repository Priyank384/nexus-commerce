package com.example.nexusCommerce.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderItemRequestDto {

    @NotNull(message = "productId must not be null")
    @Positive(message = "productId must be positive")
    private Long productId;

    @NotNull(message = "quantity must not be null")
    @Min(value = 1, message = "quantity must be at least 1")
    @Builder.Default
    private Integer quantity = 1;

    /**
     * Null is allowed: V16 gave every pre-apparel product a single LEGACY variant, so old
     * callers can still order by product id. Apparel products must pass one.
     */
    @Positive(message = "variantId must be positive")
    private Long variantId;
}
