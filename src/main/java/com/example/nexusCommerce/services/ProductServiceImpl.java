package com.example.nexusCommerce.services;

import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_ALL;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_BY_CATEGORY;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_BY_ID;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_DETAILS;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_UNIQUE_CATEGORIES;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.nexusCommerce.adapters.ProductAdapter;
import com.example.nexusCommerce.dtos.CreateProductRequestDto;
import com.example.nexusCommerce.dtos.GetProductResponseDto;
import com.example.nexusCommerce.dtos.GetProductWithDetailsResponseDto;
import com.example.nexusCommerce.dtos.UpdateProductRequestDto;
import com.example.nexusCommerce.exceptions.DuplicateResourceException;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.CategoryRepository;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.schema.Category;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.utils.PageResponse;
import com.example.nexusCommerce.utils.Pagination;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductAdapter productAdapter;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = PRODUCT_ALL, key = "#page + ':' + #size", sync = true)
    @Override
    public PageResponse<GetProductResponseDto> getAllProducts(int page, int size) {
        Page<Product> products = productRepository.findAll(Pagination.of(page, size));
        return PageResponse.of(products, productAdapter::mapToGetProductResponseDto);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = PRODUCT_BY_ID, key = "#id")
    @Override
    public GetProductResponseDto getProductById(Long id){
        return productRepository.findById(id)
                .map(productAdapter::mapToGetProductResponseDto)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id: " + id + " Not Found!"));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = PRODUCT_DETAILS, key = "#id")
    @Override
    public GetProductWithDetailsResponseDto getProductWithDetailsById(Long id){
        Product product = productRepository.findProductWithDetailsById(id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Product with id: " + id + " Not Found!"));

        return productAdapter.mapToGetProductWithDetailsResponseDto(product);
    }

    @Transactional
    @CacheEvict(cacheNames = { PRODUCT_ALL, PRODUCT_BY_ID, PRODUCT_DETAILS, PRODUCT_BY_CATEGORY,
            PRODUCT_UNIQUE_CATEGORIES }, allEntries = true)
    @Override
    public GetProductResponseDto createProduct(CreateProductRequestDto requestDto){
        Category category = categoryRepository.findById(requestDto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category with id: " + requestDto.getCategoryId() + " Not Found!"));

        if (productRepository.existsByTitleIgnoreCase(requestDto.getTitle())) {
            throw new DuplicateResourceException("Product with name '" + requestDto.getTitle() + "' already exists!");
        }

        Product newProduct = Product.builder()
                            .title(requestDto.getTitle().trim())
                            .category(category)
                            .description(requestDto.getDescription())
                            .image(requestDto.getImage())
                            .price(requestDto.getPrice())
                            .rating(requestDto.getRating())
                            .stock(requestDto.getStock() != null ? requestDto.getStock() : 0)
                            .build();

        return productAdapter.mapToGetProductResponseDto(productRepository.save(newProduct));
    }

    @Transactional
    @CacheEvict(cacheNames = { PRODUCT_ALL, PRODUCT_BY_ID, PRODUCT_DETAILS, PRODUCT_BY_CATEGORY,
            PRODUCT_UNIQUE_CATEGORIES }, allEntries = true)
    @Override
    public GetProductResponseDto updateProduct(Long id, UpdateProductRequestDto requestDto){
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id: " + id + " Not Found!"));

        if (requestDto.getTitle() != null && !requestDto.getTitle().isBlank()) {
            String title = requestDto.getTitle().trim();
            if (!product.getTitle().equalsIgnoreCase(title) && productRepository.existsByTitleIgnoreCase(title)) {
                throw new DuplicateResourceException("Product with name '" + title + "' already exists!");
            }
            product.setTitle(title);
        }
        if (requestDto.getDescription() != null) {
            product.setDescription(requestDto.getDescription());
        }
        if (requestDto.getPrice() != null) {
            if (requestDto.getPrice().signum() < 0) {
                throw new InvalidRequestException("Product price must not be negative!");
            }
            product.setPrice(requestDto.getPrice());
        }
        if (requestDto.getImage() != null) {
            product.setImage(requestDto.getImage());
        }
        if (requestDto.getRating() != null) {
            if (requestDto.getRating().signum() < 0 || requestDto.getRating().compareTo(java.math.BigDecimal.TEN) > 0) {
                throw new InvalidRequestException("Product rating must be between 0 and 10!");
            }
            product.setRating(requestDto.getRating());
        }
        if (requestDto.getStock() != null) {
            if (requestDto.getStock() < 0) {
                throw new InvalidRequestException("Stock must not be negative!");
            }
            product.setStock(requestDto.getStock());
        }
        if (requestDto.getCategoryId() != null) {
            Category category = categoryRepository.findById(requestDto.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Category with id: " + requestDto.getCategoryId() + " Not Found!"));
            product.setCategory(category);
        }

        return productAdapter.mapToGetProductResponseDto(productRepository.save(product));
    }

    @Transactional
    @CacheEvict(cacheNames = { PRODUCT_ALL, PRODUCT_BY_ID, PRODUCT_DETAILS, PRODUCT_BY_CATEGORY }, allEntries = true)
    @Override
    public GetProductResponseDto setStock(Long id, Integer stock){
        if (stock == null || stock < 0) {
            throw new InvalidRequestException("Stock must not be negative!");
        }
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id: " + id + " Not Found!"));
        product.setStock(stock);
        return productAdapter.mapToGetProductResponseDto(productRepository.save(product));
    }

    @Transactional
    @CacheEvict(cacheNames = { PRODUCT_ALL, PRODUCT_BY_ID, PRODUCT_DETAILS, PRODUCT_BY_CATEGORY,
            PRODUCT_UNIQUE_CATEGORIES }, allEntries = true)
    @Override
    public void deleteProduct(Long id){
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product with id: " + id + " Not Found!");
        }
        productRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = PRODUCT_BY_CATEGORY,
            key = "#category.trim().toLowerCase() + ':' + #page + ':' + #size", sync = true)
    @Override
    public PageResponse<GetProductWithDetailsResponseDto> findByCategory(String category, int page, int size){
        if (category == null || category.isBlank()) {
            throw new InvalidRequestException("Category name must not be blank!");
        }

        // Unknown categories are rejected before any result exists, so arbitrary search
        // terms cannot mint cache keys: exceptions are never cached.
        categoryRepository.findByNameIgnoreCase(category.trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category with name '" + category.trim() + "' Not Found!"));

        Page<Product> products = productRepository.findByCategory_NameIgnoreCase(
                category.trim(), Pagination.of(page, size));
        return PageResponse.of(products, productAdapter::mapToGetProductWithDetailsResponseDto);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = PRODUCT_UNIQUE_CATEGORIES, sync = true)
    @Override
    public List<String> getUniqueCategories() {
        return productRepository.findUniqueCategory()
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
