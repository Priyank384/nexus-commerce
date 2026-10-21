package com.example.nexusCommerce.services;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.example.nexusCommerce.adapters.ProductAdapter;
import com.example.nexusCommerce.dtos.CreateProductRequestDto;
import com.example.nexusCommerce.dtos.GetProductResponseDto;
import com.example.nexusCommerce.dtos.GetProductWithDetailsResponseDto;
import com.example.nexusCommerce.exceptions.DuplicateResourceException;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.CategoryRepository;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.schema.Category;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.utils.PageResponse;
import com.example.nexusCommerce.utils.Pagination;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Spy
    private ProductAdapter productAdapter = new ProductAdapter();

    @InjectMocks
    private ProductServiceImpl productService;

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

    private <T> Page<T> page(List<T> content) {
        return new PageImpl<>(content, PageRequest.of(0, 20), content.size());
    }

    @Test
    void getAllProducts_mapsPageOfProductsToDtos() {
        when(productRepository.findAll(any(Pageable.class))).thenReturn(page(List.of(sampleProduct())));

        PageResponse<GetProductResponseDto> result = productService.getAllProducts(0, 20);

        assertEquals(1, result.getContent().size());
        assertEquals("iPhone", result.getContent().get(0).getTitle());
        assertEquals(BigDecimal.valueOf(80000), result.getContent().get(0).getPrice());
        assertEquals(1, result.getTotalElements());
        assertTrue(result.isLast());
    }

    @Test
    void getAllProducts_clampsOversizedPageRequest() {
        when(productRepository.findAll(any(Pageable.class))).thenReturn(page(List.of()));

        productService.getAllProducts(0, 100_000);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(captor.capture());
        assertEquals(Pagination.MAX_SIZE, captor.getValue().getPageSize());
    }

    @Test
    void getProductById_returnsMappedProduct() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct()));

        GetProductResponseDto result = productService.getProductById(1L);

        assertEquals("iPhone", result.getTitle());
    }

    @Test
    void getProductById_whenNotFound_throwsResourceNotFoundException() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.getProductById(1L));
    }

    @Test
    void getProductWithDetailsById_returnsProductWithCategory() {
        when(productRepository.findProductWithDetailsById(1L)).thenReturn(List.of(sampleProduct()));

        GetProductWithDetailsResponseDto result = productService.getProductWithDetailsById(1L);

        assertEquals("iPhone", result.getTitle());
        assertEquals("Electronics", result.getCategory());
    }

    @Test
    void getProductWithDetailsById_whenNotFound_throwsResourceNotFoundException() {
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

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.existsByTitleIgnoreCase("iPhone")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenReturn(saved);

        GetProductResponseDto result = productService.createProduct(dto);

        assertEquals("iPhone", result.getTitle());
        assertEquals(BigDecimal.valueOf(80000), result.getPrice());
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

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
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

        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

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
        assertThrows(InvalidRequestException.class, () -> productService.findByCategory("  ", 0, 20));
    }

    @Test
    void findByCategory_returnsTrimmedLookupWithCategoryNames() {
        when(categoryRepository.findByNameIgnoreCase("Electronics"))
                .thenReturn(Optional.of(Category.builder().name("Electronics").build()));
        when(productRepository.findByCategory_NameIgnoreCase(eq("Electronics"), any(Pageable.class)))
                .thenReturn(page(List.of(sampleProduct())));

        PageResponse<GetProductWithDetailsResponseDto> result =
                productService.findByCategory("  Electronics  ", 0, 20);

        assertEquals(1, result.getContent().size());
        assertEquals("iPhone", result.getContent().get(0).getTitle());
        assertEquals("Electronics", result.getContent().get(0).getCategory());
    }

    @Test
    void getUniqueCategories_filtersNullCategoryNames() {
        when(productRepository.findUniqueCategory()).thenReturn(Arrays.asList("Electronics", "Books", null));

        List<String> result = productService.getUniqueCategories();

        assertEquals(2, result.size());
    }
}
