package com.example.nexusCommerce.services;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.nexusCommerce.dtos.CreateProductRequestDto;
import com.example.nexusCommerce.dtos.GetProductResponseDto;
import com.example.nexusCommerce.dtos.GetProductWithDetailsResponseDto;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.schema.Category;
import com.example.nexusCommerce.schema.Product;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final CategoryService categoryService;
    private final ProductRepository productRepository;

    public List<GetProductResponseDto> getAllProducts() {
        return productRepository.findAll()
                                .stream()
                                .map(product -> GetProductResponseDto.builder()
                                        .id(product.getId())
                                        .title(product.getTitle())
                                        .description(product.getDescription())
                                        .price(product.getPrice())
                                        .image(product.getImage())
                                        .rating(product.getRating())
                                        .build())
                                    .collect(Collectors.toList()); 
    }

    public GetProductResponseDto getProductById(Long id){

        return productRepository.findById(id)
                .map(product -> GetProductResponseDto.builder()
                    .id(product.getId())
                    .title(product.getTitle())
                    .description(product.getDescription())
                    .price(product.getPrice())
                    .image(product.getImage())
                    .rating(product.getRating())
                    .build())
                .orElseThrow(() -> new RuntimeException("Product Not Found"));

    }

    public GetProductWithDetailsResponseDto getProductWithDetailsById(Long id){
        Product product = productRepository.findProductWithDetailsById(id).get(0);
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
