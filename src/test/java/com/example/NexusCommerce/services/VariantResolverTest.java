package com.example.nexusCommerce.services;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.ProductVariantRepository;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.schema.ProductVariant;

@ExtendWith(MockitoExtension.class)
class VariantResolverTest {

    @Mock
    private ProductVariantRepository variantRepository;

    @InjectMocks
    private VariantResolver resolver;

    private Product product(Long id, String price) {
        Product product = Product.builder()
                .title("Organic Cotton Crew Tee")
                .price(new BigDecimal(price))
                .build();
        product.setId(id);
        return product;
    }

    private ProductVariant variant(Long id, Long productId, String size, String color) {
        ProductVariant variant = ProductVariant.builder()
                .sku("PT-TEE-M-BLK")
                .size(size)
                .colorName(color)
                .colorHex("#000000")
                .stock(12)
                .build();
        variant.setId(id);
        variant.setProductId(productId);
        return variant;
    }

    @Test
    void resolve_namedVariantOfTheProduct_isUsedAsGiven() {
        Product product = product(1L, "1299.00");
        ProductVariant variant = variant(11L, 1L, "M", "Black");
        when(variantRepository.findById(11L)).thenReturn(Optional.of(variant));

        assertEquals(variant, resolver.resolve(product, 11L));
        verify(variantRepository, never()).countByProductId(1L);
    }

    @Test
    void resolve_variantOfAnotherProduct_isRejectedRatherThanBorrowed() {
        // Without this check a caller could spend one product's price snapshot on another
        // product's stock row by naming a variant id from the wrong garment.
        Product product = product(1L, "1299.00");
        when(variantRepository.findById(11L)).thenReturn(Optional.of(variant(11L, 2L, "M", "Black")));

        assertThrows(InvalidRequestException.class, () -> resolver.resolve(product, 11L));
    }

    @Test
    void resolve_unknownVariantId_isNotFound() {
        when(variantRepository.findById(11L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> resolver.resolve(product(1L, "1299.00"), 11L));
    }

    @Test
    void resolve_withoutAVariantId_fallsBackToTheOnlyVariant() {
        Product product = product(1L, "1299.00");
        ProductVariant only = variant(11L, 1L, "ONE", "Default");
        when(variantRepository.countByProductId(1L)).thenReturn(1L);
        when(variantRepository.findFirstByProductIdOrderByCreatedAtAsc(1L)).thenReturn(Optional.of(only));

        assertEquals(only, resolver.resolve(product, null));
    }

    @Test
    void resolve_withoutAVariantIdOnAMultiVariantProduct_demandsOneInsteadOfGuessing() {
        // A real apparel catalogue has 6 sizes x 4 colours; silently picking the oldest would
        // ship an arbitrary garment, so the request has to fail loudly.
        Product product = product(1L, "1299.00");
        when(variantRepository.countByProductId(1L)).thenReturn(24L);

        InvalidRequestException ex = assertThrows(InvalidRequestException.class,
                () -> resolver.resolve(product, null));
        assertEquals(true, ex.getMessage().contains("variantId"));
        verify(variantRepository, never()).findFirstByProductIdOrderByCreatedAtAsc(1L);
    }

    @Test
    void resolve_whenTheCountIsOneButTheRowIsGone_refusesToReturnEmpty() {
        // Soft-delete racing between the count and the read: the line is unpurchasable, which
        // is a rejection, not a null the caller would dereference.
        Product product = product(1L, "1299.00");
        when(variantRepository.countByProductId(1L)).thenReturn(1L);
        when(variantRepository.findFirstByProductIdOrderByCreatedAtAsc(1L)).thenReturn(Optional.empty());

        assertThrows(InvalidRequestException.class, () -> resolver.resolve(product, null));
    }

    @Test
    void resolve_usesTheAssociationWhenTheReadOnlyMirrorIsStillNull() {
        // A variant saved moments earlier has no product_id mirror populated (the column is
        // insertable=false), so ownership has to be provable from the association too.
        Product product = product(1L, "1299.00");
        ProductVariant fresh = variant(11L, null, "M", "Black");
        fresh.setProduct(product);
        when(variantRepository.findById(11L)).thenReturn(Optional.of(fresh));

        assertEquals(fresh, resolver.resolve(product, 11L));
    }

    @Test
    void unitPrice_addsTheVariantDeltaToTheProductPrice() {
        ProductVariant large = variant(11L, 1L, "L", "Black");
        large.setPriceDelta(new BigDecimal("100.00"));

        assertEquals(new BigDecimal("1399.00"), resolver.unitPrice(product(1L, "1299.00"), large));
    }

    @Test
    void unitPrice_treatsAMissingDeltaAsNoChange() {
        ProductVariant legacy = variant(11L, 1L, "ONE", "Default");
        legacy.setPriceDelta(null);

        assertEquals(new BigDecimal("1299.00"), resolver.unitPrice(product(1L, "1299.00"), legacy));
    }
}
