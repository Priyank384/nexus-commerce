package com.example.nexusCommerce.dtos;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class GetProductResponseDto {
    private Long id;
    private String title;
    private String description;
    private BigDecimal price;
    private String image;
    private BigDecimal rating;
    private Integer stock;

    /** Read-only fields the storefront listing cards need without a details call. */
    private String slug;
    private String brand;
    private BigDecimal compareAtPrice;
}
