package com.example.nexusCommerce.dtos;

import java.math.BigDecimal;

import com.example.nexusCommerce.schema.Category;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductResponseDto {
    private Long id;
    private String title;
    private String description;
    private BigDecimal price;
    private String image;
    private Category category;
    private String rating;
}
