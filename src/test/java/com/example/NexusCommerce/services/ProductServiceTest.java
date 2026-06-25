package com.example.NexusCommerce.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.nexusCommerce.dtos.CreateProductRequestDto;
import com.example.nexusCommerce.dtos.GetProductResponseDto;
import com.example.nexusCommerce.dtos.GetProductWithDetailsResponseDto;
import com.example.nexusCommerce.exceptions.DuplicateResourceException;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.schema.Category;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.services.CategoryService;
import com.example.nexusCommerce.services.ProductService;
import com.example.nexusCommerce.services.cache.ProductRedisCache;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private CategoryService categoryService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductRedisCache productRedisCache;

    @InjectMocks
    private ProductService productService;

    private Product sampleProduct() {
        Category category = Category.builder().name("Electronics").build();
        category.setId(1L);
        Product product = Product.builder()
                .title("iPhone")
                .description("Latest model")
                .price(BigDecimal.valueOf(80000))
                .image("image.png")
                .rating(BigDecimal.valueOf(4.5))
                .category(category)
                .build();
        product.setId(1L);
        return product;
    }

    @Test
    void getAllProducts_whenCacheHit_returnsCachedProducts() {
        GetProductResponseDto dto = GetProductResponseDto.builder()
                .id(1L)
                .title("iPhone")
                .price(BigDecimal.valueOf(80000))
                .rating(BigDecimal.valueOf(4.5))
                .build();
        when(productRedisCache.getAllSummaries()).thenReturn(Optional.of(List.of(dto)));

        List<GetProductResponseDto> result = productService.getAllProducts();

        assertEquals(1, result.size());
        assertEquals("iPhone", result.get(0).getTitle());
        verify(productRepository, never()).findAll();
    }

    @Test
    void getAllProducts_whenCacheMiss_loadsFromRepositoryAndCaches() {
        Product product = sampleProduct();
        when(productRedisCache.getAllSummaries()).thenReturn(Optional.empty());
        when(productRepository.findAll()).thenReturn(List.of(product));

        List<GetProductResponseDto> result = productService.getAllProducts();

        assertEquals(1, result.size());
        assertEquals("iPhone", result.get(0).getTitle());
        verify(productRedisCache).putAllSummaries(result);
    }

    @Test
    void getProductById_whenCacheHit_returnsCachedProduct() {
        GetProductResponseDto dto = GetProductResponseDto.builder().id(1L).title("iPhone").build();
        when(productRedisCache.getSummary(1L)).thenReturn(Optional.of(dto));

        GetProductResponseDto result = productService.getProductById(1L);

        assertEquals("iPhone", result.getTitle());
        verify(productRepository, never()).findById(1L);
    }

    @Test
    void getProductById_whenCacheMiss_loadsFromRepositoryAndCaches() {
        Product product = sampleProduct();
        when(productRedisCache.getSummary(1L)).thenReturn(Optional.empty());
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        GetProductResponseDto result = productService.getProductById(1L);

        assertEquals("iPhone", result.getTitle());
        verify(productRedisCache).putSummary(1L, result);
    }

    @Test
    void getProductById_whenNotFound_throwsResourceNotFoundException() {
        when(productRedisCache.getSummary(1L)).thenReturn(Optional.empty());
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.getProductById(1L));
    }

    @Test
    void getProductWithDetailsById_whenCacheMiss_returnsProductWithCategory() {
        Product product = sampleProduct();
        when(productRedisCache.getDetails(1L)).thenReturn(Optional.empty());
        when(productRepository.findProductWithDetailsById(1L)).thenReturn(List.of(product));

        GetProductWithDetailsResponseDto result = productService.getProductWithDetailsById(1L);

        assertEquals("iPhone", result.getTitle());
        assertEquals("Electronics", result.getCategory());
        verify(productRedisCache).putDetails(1L, result);
    }

    @Test
    void getProductWithDetailsById_whenNotFound_throwsResourceNotFoundException() {
        when(productRedisCache.getDetails(1L)).thenReturn(Optional.empty());
        when(productRepository.findProductWithDetailsById(1L)).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> productService.getProductWithDetailsById(1L));
    }

    @Test
    void createProduct_savesAndReturnsProduct() {
        Category category = Category.builder().name("Electronics").build();
        category.setId(1L);
        CreateProductRequestDto dto = CreateProductRequestDto.builder()
                .title("iPhone")
                .description("Latest model")
                .price(BigDecimal.valueOf(80000))
                .image("image.png")
                .rating(BigDecimal.valueOf(4.5))
                .categoryId(1L)
                .build();
        Product saved = sampleProduct();

        when(categoryService.getCategoryById(1L)).thenReturn(category);
        when(productRepository.existsByTitleIgnoreCase("iPhone")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenReturn(saved);

        Product result = productService.createProduct(dto);

        assertEquals("iPhone", result.getTitle());
        assertEquals("Electronics", result.getCategory().getName());
    }

    @Test
    void createProduct_whenTitleBlank_throwsInvalidRequestException() {
        CreateProductRequestDto dto = CreateProductRequestDto.builder()
                .title("  ")
                .price(BigDecimal.TEN)
                .rating(BigDecimal.ONE)
                .categoryId(1L)
                .build();

        assertThrows(InvalidRequestException.class, () -> productService.createProduct(dto));
    }

    @Test
    void createProduct_whenDuplicateTitle_throwsDuplicateResourceException() {
        CreateProductRequestDto dto = CreateProductRequestDto.builder()
                .title("iPhone")
                .price(BigDecimal.TEN)
                .rating(BigDecimal.ONE)
                .categoryId(1L)
                .build();
        Category category = Category.builder().name("Electronics").build();
        category.setId(1L);

        when(categoryService.getCategoryById(1L)).thenReturn(category);
        when(productRepository.existsByTitleIgnoreCase("iPhone")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> productService.createProduct(dto));
    }

    @Test
    void createProduct_whenCategoryNotFound_throwsResourceNotFoundException() {
        CreateProductRequestDto dto = CreateProductRequestDto.builder()
                .title("iPhone")
                .price(BigDecimal.TEN)
                .rating(BigDecimal.ONE)
                .categoryId(99L)
                .build();

        when(categoryService.getCategoryById(99L))
                .thenThrow(new ResourceNotFoundException("Category with id: 99 Not Found!"));

        assertThrows(ResourceNotFoundException.class, () -> productService.createProduct(dto));
    }

    @Test
    void deleteProduct_deletesWhenProductExists() {
        when(productRepository.existsById(1L)).thenReturn(true);

        productService.deleteProduct(1L);

        verify(productRepository).deleteById(1L);
    }

    @Test
    void deleteProduct_whenNotFound_throwsResourceNotFoundException() {
        when(productRepository.existsById(1L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> productService.deleteProduct(1L));
    }

    @Test
    void findByCategory_whenCategoryBlank_throwsInvalidRequestException() {
        assertThrows(InvalidRequestException.class, () -> productService.findByCategory("  "));
    }

    @Test
    void findByCategory_whenCacheMiss_loadsFromRepositoryAndCaches() {
        Product product = sampleProduct();
        when(productRedisCache.getByCategory("Electronics")).thenReturn(Optional.empty());
        when(productRepository.findByCategory_NameIgnoreCase("Electronics")).thenReturn(List.of(product));

        List<Product> result = productService.findByCategory("  Electronics  ");

        assertEquals(1, result.size());
        verify(productRedisCache).putByCategory("Electronics", result);
    }

    @Test
    void getUniqueCategories_whenCacheMiss_loadsFromRepositoryAndCaches() {
        when(productRedisCache.getUniqueCategories()).thenReturn(Optional.empty());
        when(productRepository.findUniqueCategory()).thenReturn(List.of("Electronics", "Books"));

        List<String> result = productService.getUniqueCategories();

        assertEquals(2, result.size());
        verify(productRedisCache).putUniqueCategories(result);
    }
}
