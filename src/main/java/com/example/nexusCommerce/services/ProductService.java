package com.example.nexusCommerce.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.nexusCommerce.dtos.CreateProductRequestDto;
import com.example.nexusCommerce.dtos.ProductResponseDto;
import com.example.nexusCommerce.exceptions.ProductNotFoundException;
import com.example.nexusCommerce.mapper.ProductMapper;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.schema.Category;
import com.example.nexusCommerce.schema.Product;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final CategoryService categoryService;
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
        Category newcategory = categoryService.getCategoryById(requestDto.getCategoryId());

        Product newProduct = Product.builder()
                            .title(requestDto.getTitle())
                            .category(newcategory)
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
