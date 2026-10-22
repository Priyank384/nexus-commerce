package com.example.nexusCommerce.dtos;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetVariantResponseDto {
    private Long id;
    private Long productId;
    private String sku;
    private String size;
    private String colorName;
    private String colorHex;
    private BigDecimal priceDelta;
    private String imageUrl;
    private Integer stock;
    /** price + priceDelta, computed on read. */
    private BigDecimal price;
}
