package com.example.nexusCommerce.services;

import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_ALL;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_BY_CATEGORY;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_BY_ID;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_BY_SLUG;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_DETAILS;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_SEARCH;
import static com.example.nexusCommerce.configs.CacheConfig.VARIANT_COLOR_VALUES;
import static com.example.nexusCommerce.configs.CacheConfig.VARIANT_SIZE_VALUES;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.nexusCommerce.adapters.ProductAdapter;
import com.example.nexusCommerce.dtos.CreateVariantRequestDto;
import com.example.nexusCommerce.dtos.GetVariantResponseDto;
import com.example.nexusCommerce.dtos.UpdateVariantRequestDto;
import com.example.nexusCommerce.exceptions.DuplicateResourceException;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.repositories.ProductVariantRepository;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.schema.ProductVariant;

import lombok.RequiredArgsConstructor;

/**
 * Variants are the source of truth for apparel stock and price, so every write here also
 * re-mirrors the legacy {@code products.stock} aggregate and evicts the search caches that
 * filter on size and colour.
 */
@Service
@RequiredArgsConstructor
public class VariantServiceImpl implements VariantService {

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final ProductAdapter productAdapter;


    @Transactional
    @CacheEvict(cacheNames = { PRODUCT_ALL, PRODUCT_BY_ID, PRODUCT_BY_SLUG, PRODUCT_DETAILS,
            PRODUCT_BY_CATEGORY, PRODUCT_SEARCH, VARIANT_SIZE_VALUES, VARIANT_COLOR_VALUES }, allEntries = true)
    @Override
    public GetVariantResponseDto create(Long productId, CreateVariantRequestDto requestDto) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id: " + productId + " Not Found!"));

        String sku = normalizeSku(requestDto.getSku());
        rejectExistingSku(sku, null);
        rejectExistingOption(product.getId(), requestDto.getSize().trim(), requestDto.getColorName().trim(), null);

        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku(sku)
                .size(requestDto.getSize().trim())
                .colorName(requestDto.getColorName().trim())
                .colorHex(normalizeHex(requestDto.getColorHex()))
                .priceDelta(requestDto.getPriceDelta() != null ? requestDto.getPriceDelta() : BigDecimal.ZERO)
                .imageUrl(requestDto.getImageUrl())
                .stock(requestDto.getStock() != null ? requestDto.getStock() : 0)
                .build();

        ProductVariant saved = variantRepository.save(variant);
        resyncProductStock(product);
        return productAdapter.mapToGetVariantResponseDto(saved, product.getPrice());
    }

    @Transactional
    @CacheEvict(cacheNames = { PRODUCT_ALL, PRODUCT_BY_ID, PRODUCT_BY_SLUG, PRODUCT_DETAILS,
            PRODUCT_BY_CATEGORY, PRODUCT_SEARCH, VARIANT_SIZE_VALUES, VARIANT_COLOR_VALUES }, allEntries = true)
    @Override
    public GetVariantResponseDto update(Long variantId, UpdateVariantRequestDto requestDto) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant with id: " + variantId + " Not Found!"));
        Product product = variant.getProduct();

        if (requestDto.getSku() != null && !requestDto.getSku().isBlank()) {
            String sku = normalizeSku(requestDto.getSku());
            if (!sku.equals(variant.getSku())) {
                rejectExistingSku(sku, variantId);
                variant.setSku(sku);
            }
        }
        if (requestDto.getSize() != null && !requestDto.getSize().isBlank()) {
            variant.setSize(requestDto.getSize().trim());
        }
        if (requestDto.getColorName() != null && !requestDto.getColorName().isBlank()) {
            variant.setColorName(requestDto.getColorName().trim());
        }
        if (requestDto.getColorHex() != null && !requestDto.getColorHex().isBlank()) {
            variant.setColorHex(normalizeHex(requestDto.getColorHex()));
        }
        if (requestDto.getPriceDelta() != null) {
            if (requestDto.getPriceDelta().signum() < 0) {
                throw new InvalidRequestException("Price delta must not be negative!");
            }
            variant.setPriceDelta(requestDto.getPriceDelta());
        }
        if (requestDto.getImageUrl() != null) {
            variant.setImageUrl(requestDto.getImageUrl());
        }
        if (requestDto.getStock() != null) {
            if (requestDto.getStock() < 0) {
                throw new InvalidRequestException("Stock must not be negative!");
            }
            variant.setStock(requestDto.getStock());
        }

        // (product, size, colour) is unique among live rows, and the checks above let each
        // field through one at a time, so the combined option is validated after merging.
        rejectExistingOption(product.getId(), variant.getSize(), variant.getColorName(), variantId);

        ProductVariant saved = variantRepository.save(variant);
        resyncProductStock(product);
        return productAdapter.mapToGetVariantResponseDto(saved, product.getPrice());
    }

    @Transactional
    @CacheEvict(cacheNames = { PRODUCT_ALL, PRODUCT_BY_ID, PRODUCT_BY_SLUG, PRODUCT_DETAILS,
            PRODUCT_BY_CATEGORY, PRODUCT_SEARCH, VARIANT_SIZE_VALUES, VARIANT_COLOR_VALUES }, allEntries = true)
    @Override
    public void delete(Long variantId) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant with id: " + variantId + " Not Found!"));
        Product product = variant.getProduct();

        // A product with no variant cannot be added to a cart or ordered, so the last one is
        // protected; delete the product itself if the whole style is going away.
        if (variantRepository.countByProductId(product.getId()) <= 1) {
            throw new InvalidRequestException("Variant " + variantId + " is the only variant of product "
                    + product.getId() + "; delete the product instead of its last variant");
        }
        variantRepository.deleteById(variantId);
        resyncProductStock(product);
    }

    /**
     * products.stock is a mirror of the variant rows. Recomputing it after every variant write
     * is what stops the legacy column and the real stock from drifting apart, which would make
     * the order-time oversell guard reject buyers who could actually be served.
     */
    private void resyncProductStock(Product product) {
        long total = variantRepository.sumStockByProductId(product.getId());
        product.setStock((int) total);
        productRepository.save(product);
    }

    private String normalizeSku(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidRequestException("SKU must not be blank!");
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeHex(String raw) {
        if (raw == null || !raw.matches("^#[0-9A-Fa-f]{6}$")) {
            throw new InvalidRequestException("Colour hex must be a #RRGGBB value!");
        }
        return raw.toUpperCase(Locale.ROOT);
    }

    private void rejectExistingSku(String sku, Long allowedId) {
        variantRepository.findBySku(sku)
                .filter(other -> !other.getId().equals(allowedId))
                .ifPresent(other -> {
                    throw new DuplicateResourceException("Variant with SKU '" + sku + "' already exists!");
                });
    }

    private void rejectExistingOption(Long productId, String size, String colorName, Long allowedId) {
        boolean clash = variantRepository.findByProductIdOrderBySizeAscColorNameAsc(productId).stream()
                .filter(other -> !other.getId().equals(allowedId))
                .anyMatch(other -> other.getSize().equalsIgnoreCase(size)
                        && other.getColorName().equalsIgnoreCase(colorName));
        if (clash) {
            throw new DuplicateResourceException(
                    "Product " + productId + " already has a variant in size '" + size
                            + "' colour '" + colorName + "'");
        }
    }
}
