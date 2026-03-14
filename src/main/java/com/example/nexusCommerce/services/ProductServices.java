package com.example.nexusCommerce.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.nexusCommerce.dtos.CreateProductRequestDto;
import com.example.nexusCommerce.dtos.ProductResponseDto;
import com.example.nexusCommerce.exceptions.ProductNotFoundException;
import com.example.nexusCommerce.mapper.ProductMapper;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.schema.Product;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductServices {
    private final ProductRepository productRepository;

    public List<ProductResponseDto> getAllProducts() {
        return productRepository.findAll()
                                .stream()
                                .map(ProductMapper::toDto)
                                .toList();
    }

    public ProductResponseDto getProductById(Long id){
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        return ProductMapper.toDto(product);
    }
    public Product createProduct(CreateProductRequestDto requestDto){
        Product newProduct = Product.builder()
                            .title(requestDto.getTitle())
                            // .category(requestDto.getCategory())
                            .description(requestDto.getDescription())
                            .image(requestDto.getImage())
                            .price(requestDto.getPrice())
                            .rating(requestDto.getRating()).build();

        return productRepository.save(newProduct);
    }
    public void deleteProduct(Long id){
        productRepository.deleteById(id);
    }
    public List<Product>findByCategory(String category){
        return productRepository.findByCategory(category);
    }

    public List<String> getUniqueCategories() {
        
        return productRepository.findUniqueCategory();
    }
}
