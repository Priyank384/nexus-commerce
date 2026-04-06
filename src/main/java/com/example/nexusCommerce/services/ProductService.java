package com.example.nexusCommerce.services;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.nexusCommerce.dtos.CreateProductRequestDto;
import com.example.nexusCommerce.dtos.GetProductResponseDto;
import com.example.nexusCommerce.dtos.GetProductWithDetailsResponseDto;
import com.example.nexusCommerce.exceptions.DuplicateResourceException;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
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
                                .map(this::toGetProductResponseDto)
                                    .collect(Collectors.toList()); 
    }

    public GetProductResponseDto getProductById(Long id){
        return productRepository.findById(id)
                .map(this::toGetProductResponseDto)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id: " + id + " Not Found!"));
    }

    public GetProductWithDetailsResponseDto getProductWithDetailsById(Long id){
        Product product = productRepository.findProductWithDetailsById(id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Product with id: " + id + " Not Found!"));
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
        validateCreateProductRequest(requestDto);
        Category newcategory = categoryService.getCategoryById(requestDto.getCategoryId());

        if (productRepository.existsByTitleIgnoreCase(requestDto.getTitle())) {
            throw new DuplicateResourceException("Product with name '" + requestDto.getTitle() + "' already exists!");
        }

        Product newProduct = Product.builder()
                            .title(requestDto.getTitle().trim())
                            .category(newcategory)
                            .description(requestDto.getDescription())
                            .image(requestDto.getImage())
                            .price(requestDto.getPrice())
                            .rating(requestDto.getRating()).build();

        return productRepository.save(newProduct);
    }
    public void deleteProduct(Long id){
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product with id: " + id + " Not Found!");
        }
        productRepository.deleteById(id);
    }
    public List<Product>findByCategory(String category){
        if (category == null || category.isBlank()) {
            throw new InvalidRequestException("Category name must not be blank!");
        }
        return productRepository.findByCategory_NameIgnoreCase(category.trim());
    }

    public List<String> getUniqueCategories() {
        return productRepository.findUniqueCategory()
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private GetProductResponseDto toGetProductResponseDto(Product product) {
        return GetProductResponseDto.builder()
                .id(product.getId())
                .title(product.getTitle())
                .description(product.getDescription())
                .price(product.getPrice())
                .image(product.getImage())
                .rating(product.getRating())
                .build();
    }

    private void validateCreateProductRequest(CreateProductRequestDto requestDto) {
        if (requestDto == null) {
            throw new InvalidRequestException("Request body must not be null!");
        }
        if (requestDto.getTitle() == null || requestDto.getTitle().isBlank()) {
            throw new InvalidRequestException("Product title must not be blank!");
        }
        if (requestDto.getPrice() == null) {
            throw new InvalidRequestException("Product price must not be null!");
        }
        if (requestDto.getRating() == null) {
            throw new InvalidRequestException("Product rating must not be null!");
        }
        if (requestDto.getCategoryId() == null) {
            throw new InvalidRequestException("Category id must not be null!");
        }
    }
}
