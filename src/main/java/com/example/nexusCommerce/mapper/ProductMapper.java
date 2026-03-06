package com.example.nexusCommerce.mapper;

import com.example.nexusCommerce.dtos.ProductResponseDto;
import com.example.nexusCommerce.schema.Product;

public class ProductMapper {
    
    public static ProductResponseDto toDto(Product product){
        return ProductResponseDto.builder()
                                .id(product.getId())
                                .title(product.getTitle())
                                .category(product.getCategory())
                                .description(product.getDescription())
                                .image(product.getImage())
                                .price(product.getPrice())
                                .rating(product.getRating())
                                .build();

    }
}
