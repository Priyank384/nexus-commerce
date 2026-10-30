package com.example.nexusCommerce.services;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.example.nexusCommerce.dtos.UpdateProductRequestDto;
import com.example.nexusCommerce.exceptions.DuplicateResourceException;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.CategoryRepository;
import com.example.nexusCommerce.repositories.CartItemRepository;
import com.example.nexusCommerce.repositories.ProductImageRepository;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.repositories.ProductVariantRepository;
import com.example.nexusCommerce.repositories.WishlistItemRepository;
import com.example.nexusCommerce.schema.Category;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.schema.ProductImage;
import com.example.nexusCommerce.schema.ProductVariant;
import com.example.nexusCommerce.utils.PageResponse;
import com.example.nexusCommerce.utils.Pagination;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductVariantRepository variantRepository;

    @Mock
    private ProductImageRepository imageRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private WishlistItemRepository wishlistItemRepository;

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
        when(productRepository.findByIsActiveTrue(any(Pageable.class)))
                .thenReturn(page(List.of(sampleProduct())));

        PageResponse<GetProductResponseDto> result = productService.getAllProducts(0, 20);

        assertEquals(1, result.getContent().size());
        assertEquals("iPhone", result.getContent().get(0).getTitle());
        assertEquals(BigDecimal.valueOf(80000), result.getContent().get(0).getPrice());
        assertEquals("Electronics", result.getContent().get(0).getCategory());
        assertTrue(result.getContent().get(0).getIsActive());
        assertEquals(1, result.getTotalElements());
        assertTrue(result.isLast());
    }

    @Test
    void getAllProducts_clampsOversizedPageRequest() {
        when(productRepository.findByIsActiveTrue(any(Pageable.class))).thenReturn(page(List.of()));

        productService.getAllProducts(0, 100_000);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findByIsActiveTrue(captor.capture());
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
    void getProductById_hidesAnUnpublishedDraft() {
        Product draft = sampleProduct();
        draft.setIsActive(false);
        when(productRepository.findById(1L)).thenReturn(Optional.of(draft));

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
    void getProductWithDetailsById_hidesAnUnpublishedDraft() {
        Product draft = sampleProduct();
        draft.setIsActive(false);
        when(productRepository.findProductWithDetailsById(1L)).thenReturn(List.of(draft));

        assertThrows(ResourceNotFoundException.class, () -> productService.getProductWithDetailsById(1L));
    }

    @Test
    void getProductWithDetailsForAdmin_returnsAnUnpublishedDraft() {
        Product draft = sampleProduct();
        draft.setIsActive(false);
        when(productRepository.findProductWithDetailsById(1L)).thenReturn(List.of(draft));

        GetProductWithDetailsResponseDto result = productService.getProductWithDetailsForAdmin(1L);

        assertEquals("iPhone", result.getTitle());
        assertFalse(result.getIsActive());
    }

    @Test
    void getProductBySlug_hidesAnUnpublishedDraft() {
        Product draft = sampleProduct();
        draft.setIsActive(false);
        draft.setSlug("organic-tee");
        when(productRepository.findBySlugIgnoreCase("organic-tee")).thenReturn(Optional.of(draft));

        assertThrows(ResourceNotFoundException.class, () -> productService.getProductBySlug("organic-tee"));
    }

    @Test
    void getVariants_hidesAnUnpublishedDraft() {
        Product draft = sampleProduct();
        draft.setIsActive(false);
        when(productRepository.findById(1L)).thenReturn(Optional.of(draft));

        assertThrows(ResourceNotFoundException.class, () -> productService.getVariants(1L));
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
    void updateProduct_priceOnlyKeepsTheExistingCompareAt() {
        Product product = sampleProduct();
        product.setCompareAtPrice(BigDecimal.valueOf(90000));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        productService.updateProduct(1L, UpdateProductRequestDto.builder()
                .price(BigDecimal.valueOf(70000))
                .build());

        assertEquals(BigDecimal.valueOf(70000), product.getPrice());
        assertEquals(BigDecimal.valueOf(90000), product.getCompareAtPrice());
    }

    @Test
    void updateProduct_compareAtBelowTheMergedPriceIsRejected() {
        Product product = sampleProduct();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        // Only compare-at comes in, so the rule has to be checked against the price already on
        // the row — reading the pair off the request used to NPE here instead.
        assertThrows(InvalidRequestException.class, () -> productService.updateProduct(1L,
                UpdateProductRequestDto.builder().compareAtPrice(BigDecimal.valueOf(60000)).build()));
    }

    @Test
    void updateProduct_clearFlagTakesTheProductOffSale() {
        Product product = sampleProduct();
        product.setCompareAtPrice(BigDecimal.valueOf(90000));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        productService.updateProduct(1L, UpdateProductRequestDto.builder()
                .clearCompareAtPrice(true)
                .build());

        assertNull(product.getCompareAtPrice());
    }

    @Test
    void updateProduct_clearFlagWinsOverASuppliedCompareAt() {
        Product product = sampleProduct();
        product.setCompareAtPrice(BigDecimal.valueOf(90000));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        productService.updateProduct(1L, UpdateProductRequestDto.builder()
                .compareAtPrice(BigDecimal.valueOf(95000))
                .clearCompareAtPrice(true)
                .build());

        assertNull(product.getCompareAtPrice());
    }

    @Test
    void updateProduct_blankGenderClearsIt() {
        Product product = sampleProduct();
        product.setGender("MEN");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        // The editor sends "" for an unset field; the DTO must accept it and the service
        // must store null rather than the empty string.
        productService.updateProduct(1L, UpdateProductRequestDto.builder().gender("").build());

        assertNull(product.getGender());
    }

    @Test
    void updateProduct_rejectsARatingAboveTheFiveStarScale() {
        Product product = sampleProduct();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThrows(InvalidRequestException.class, () -> productService.updateProduct(1L,
                UpdateProductRequestDto.builder().rating(BigDecimal.valueOf(5.1)).build()));
    }

    @Test
    void createProduct_compareAtBelowPriceIsRejected() {
        Category category = Category.builder().name("Electronics").build();
        category.setId(1L);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.existsByTitleIgnoreCase("iPhone")).thenReturn(false);

        assertThrows(InvalidRequestException.class, () -> productService.createProduct(
                CreateProductRequestDto.builder()
                        .title("iPhone")
                        .price(BigDecimal.valueOf(80000))
                        .compareAtPrice(BigDecimal.valueOf(70000))
                        .rating(BigDecimal.valueOf(4.5))
                        .categoryId(1L)
                        .build()));
    }

    @Test
    void deleteProduct_deletesWhenProductExists() {
        when(productRepository.existsById(1L)).thenReturn(true);

        productService.deleteProduct(1L);

        verify(productRepository).deleteById(1L);
    }

    @Test
    void deleteProduct_retiresTheVariantsAndImagesThatBelongedToIt() {
        Product product = sampleProduct();
        ProductVariant medium = ProductVariant.builder().product(product).sku("PT-1").size("M")
                .colorName("Black").colorHex("#000000").stock(4).build();
        medium.setId(11L);
        ProductVariant large = ProductVariant.builder().product(product).sku("PT-2").size("L")
                .colorName("Black").colorHex("#000000").stock(4).build();
        large.setId(12L);
        ProductImage hero = ProductImage.builder().product(product).url("/uploads/products/a.png")
                .position(0).build();
        hero.setId(21L);

        when(productRepository.existsById(1L)).thenReturn(true);
        when(variantRepository.findByProductIdOrderBySizeAscColorNameAsc(1L))
                .thenReturn(List.of(medium, large));
        when(imageRepository.findByProductIdOrderByPositionAsc(1L)).thenReturn(List.of(hero));
        when(cartItemRepository.retireByProduct(1L)).thenReturn(1);
        when(wishlistItemRepository.retireByProduct(1L)).thenReturn(1);

        productService.deleteProduct(1L);

        verify(variantRepository).deleteById(11L);
        verify(variantRepository).deleteById(12L);
        verify(imageRepository).delete(hero);
        verify(productRepository).deleteById(1L);
        // A cart line whose product no longer resolves makes GET /cart throw for that shopper,
        // so live carts and saved wishes are retired with the style.
        verify(cartItemRepository).retireByProduct(1L);
        verify(wishlistItemRepository).retireByProduct(1L);
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
        when(categoryRepository.findFirstByNameIgnoreCaseOrderByIdAsc("Electronics"))
                .thenReturn(Optional.of(Category.builder().name("Electronics").build()));
        when(productRepository.findByCategory_NameIgnoreCaseAndIsActiveTrue(
                eq("Electronics"), any(Pageable.class)))
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
