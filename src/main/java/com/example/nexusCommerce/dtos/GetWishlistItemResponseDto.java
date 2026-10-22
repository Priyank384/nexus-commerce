package com.example.nexusCommerce.dtos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetWishlistItemResponseDto {
    private Long id;
    private Long productId;
    private String productTitle;
    private String productImage;
    private BigDecimal price;

    private Long variantId;
    private String sku;
    private String size;
    private String colorName;
    private String colorHex;

    private LocalDateTime addedAt;
}
