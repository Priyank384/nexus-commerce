package com.example.nexusCommerce.dtos;

import com.example.nexusCommerce.schema.OrderItemAction;

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
public class OrderItemActionDto {

    @NotNull(message = "productId must not be null")
    @Positive(message = "productId must be positive")
    private Long productId;

    @Min(value = 1, message = "quantity must be at least 1")
    private Integer quantity;

    @NotNull(message = "action must not be null")
    private OrderItemAction action;

    /**
     * Identifies which size+colour line to act on. Omitting it only resolves for products
     * that carry a single variant, which is what V16 backfilled for pre-apparel rows.
     */
    @Positive(message = "variantId must be positive")
    private Long variantId;
}
