package com.example.nexusCommerce.mapper;

import com.example.nexusCommerce.dtos.ProductResponseDto;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.schema.Category;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class ProductMapper {
    public static ProductResponseDto toDto(Product product){
        Category category = product.getCategory();
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
