package com.example.nexusCommerce.adapters;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.nexusCommerce.dtos.GetProductResponseDto;
import com.example.nexusCommerce.dtos.GetProductWithDetailsResponseDto;
import com.example.nexusCommerce.schema.Product;

@Component
public class ProductAdapter {

    public GetProductResponseDto mapToGetProductResponseDto(Product product) {
        return GetProductResponseDto.builder()
                .id(product.getId())
                .title(product.getTitle())
                .description(product.getDescription())
                .price(product.getPrice())
                .image(product.getImage())
                .rating(product.getRating())
                .stock(product.getStock())
                .build();
    }

    public List<GetProductResponseDto> mapToGetProductResponseDtoList(List<Product> products) {
        return products.stream().map(this::mapToGetProductResponseDto).collect(Collectors.toList());
    }

    /**
     * Only safe for products whose category is already initialised: reading the name on a
     * lazy proxy outside a transaction throws.
     */
    public GetProductWithDetailsResponseDto mapToGetProductWithDetailsResponseDto(Product product) {
        return GetProductWithDetailsResponseDto.builder()
                .id(product.getId())
                .title(product.getTitle())
                .description(product.getDescription())
                .price(product.getPrice())
                .image(product.getImage())
                .rating(product.getRating())
                .category(product.getCategory().getName())
                .build();
    }
}
