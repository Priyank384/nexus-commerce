package com.example.nexusCommerce.dtos;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemResponseDto {
    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal productPrice;
    private String productImage;
    private BigDecimal subTotal;
}
