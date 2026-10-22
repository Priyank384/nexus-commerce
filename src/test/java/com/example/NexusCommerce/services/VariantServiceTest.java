package com.example.nexusCommerce.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.nexusCommerce.adapters.ProductAdapter;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.CartItemRepository;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.repositories.ProductVariantRepository;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.schema.ProductVariant;

@ExtendWith(MockitoExtension.class)
class VariantServiceTest {

    @Mock
    private ProductVariantRepository variantRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductAdapter productAdapter;

    @InjectMocks
    private VariantServiceImpl variantService;

    private Product product() {
        Product product = Product.builder()
                .title("Crew Tee")
                .description("Tee")
                .price(new BigDecimal("890.00").setScale(2, RoundingMode.HALF_UP))
                .rating(BigDecimal.valueOf(4.5))
                .stock(8)
                .build();
        product.setId(1L);
        return product;
    }

    private ProductVariant variant(Product product, Long id, String size) {
        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku("TEE-" + size + "-BLACK")
                .size(size)
                .colorName("Black")
                .colorHex("#000000")
                .priceDelta(BigDecimal.ZERO)
                .stock(4)
                .build();
        variant.setId(id);
        return variant;
    }

    @Test
    void delete_retiresCartLinesForThatSizeAndResyncsTheStockMirror() {
        Product product = product();
        ProductVariant medium = variant(product, 11L, "M");

        when(variantRepository.findById(11L)).thenReturn(java.util.Optional.of(medium));
        when(variantRepository.countByProductId(1L)).thenReturn(2L);
        when(variantRepository.sumStockByProductId(1L)).thenReturn(4L);
        when(cartItemRepository.retireByVariant(11L)).thenReturn(1);

        variantService.delete(11L);

        verify(variantRepository).deleteById(11L);
        verify(productRepository).save(product);
        // A line whose variant is gone cannot be priced or stock-checked, so GET /cart would
        // throw for every shopper who had picked that size.
        verify(cartItemRepository).retireByVariant(11L);
    }

    @Test
    void delete_refusesToRemoveTheLastVariantAndTouchesNoCarts() {
        Product product = product();

        when(variantRepository.findById(11L)).thenReturn(java.util.Optional.of(variant(product, 11L, "M")));
        when(variantRepository.countByProductId(1L)).thenReturn(1L);

        assertThrows(InvalidRequestException.class, () -> variantService.delete(11L));

        verify(variantRepository, never()).deleteById(anyLong());
        verify(cartItemRepository, never()).retireByVariant(any());
    }

    @Test
    void delete_throwsWhenTheVariantDoesNotExist() {
        when(variantRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> variantService.delete(99L));

        verify(cartItemRepository, never()).retireByVariant(any());
    }
}
