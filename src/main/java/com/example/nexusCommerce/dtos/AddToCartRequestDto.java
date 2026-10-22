package com.example.nexusCommerce.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AddToCartRequestDto {
    @NotNull
    private Long productId;
    @NotNull
    @Min(1)
    private Integer quantity;

    /**
     * Required for apparel products once the storefront adds by variant. Null falls back to
     * the product's single LEGACY variant, which is what V16 created for pre-apparel rows.
     */
    @Positive
    private Long variantId;
}
