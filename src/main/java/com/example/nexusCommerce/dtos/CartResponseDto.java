package com.example.nexusCommerce.dtos;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CartResponseDto {
    private Long id;
    private Long userId;
    private List<CartItemResponseDto> items;
    /** Sum of every line total; zero for an empty cart. */
    private BigDecimal grandTotal;
    /** Number of physical items (sum of line quantities), handy for a UI badge. */
    private Integer itemCount;
}
