package com.example.nexusCommerce.dtos;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CartItemResponseDto {
    private Long id;
    private Long productId;
    private String productName;
    private String productImage;
    private BigDecimal unitPrice;
    private Integer quantity;
    /** unitPrice * quantity, computed on read. */
    private BigDecimal lineTotal;

    private Long variantId;
    private String sku;
    private String variantSize;
    private String variantColorName;
    private String variantColorHex;
    /** Stock left on the selected variant; null when the line is not variant-scoped. */
    private Integer variantStock;
}
