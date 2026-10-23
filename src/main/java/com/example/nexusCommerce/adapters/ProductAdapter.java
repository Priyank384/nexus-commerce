package com.example.nexusCommerce.adapters;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.nexusCommerce.dtos.GetProductImageResponseDto;
import com.example.nexusCommerce.dtos.GetProductResponseDto;
import com.example.nexusCommerce.dtos.GetProductWithDetailsResponseDto;
import com.example.nexusCommerce.dtos.GetVariantResponseDto;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.schema.ProductImage;
import com.example.nexusCommerce.schema.ProductVariant;

@Component
public class ProductAdapter {

    /**
     * Requires the product's category to be initialised: reading the name on a lazy proxy
     * outside a transaction throws. Every caller either runs inside a transaction or loaded
     * the row with a category fetch join (see {@code ProductRepository.findByIsActiveTrue}).
     */
    public GetProductResponseDto mapToGetProductResponseDto(Product product) {
        return GetProductResponseDto.builder()
                .id(product.getId())
                .title(product.getTitle())
                .description(product.getDescription())
                .price(product.getPrice())
                .image(product.getImage())
                .rating(product.getRating())
                .reviewCount(product.getReviewCount())
                .stock(product.getStock())
                .slug(product.getSlug())
                .brand(product.getBrand())
                .compareAtPrice(product.getCompareAtPrice())
                .category(product.getCategory().getName())
                .categoryId(product.getCategory().getId())
                .isActive(product.getIsActive())
                .build();
    }

    public List<GetProductResponseDto> mapToGetProductResponseDtoList(List<Product> products) {
        return products.stream().map(this::mapToGetProductResponseDto).collect(Collectors.toList());
    }

    /**
     * Only safe for products whose category is already initialised: reading the name on a
     * lazy proxy outside a transaction throws. Variants and gallery images are left empty --
     * use the overload that takes them once the service has loaded them.
     */
    public GetProductWithDetailsResponseDto mapToGetProductWithDetailsResponseDto(Product product) {
        return mapToGetProductWithDetailsResponseDto(product, List.of(), List.of());
    }

    /**
     * open-in-view is off, so {@code product.getVariants()} and {@code getImages()} are lazy
     * proxies outside a transaction. The service loads both inside its read-only transaction
     * and hands them over rather than letting this mapping trigger a query.
     */
    public GetProductWithDetailsResponseDto mapToGetProductWithDetailsResponseDto(
            Product product, List<ProductVariant> variants, List<ProductImage> images) {
        return GetProductWithDetailsResponseDto.builder()
                .id(product.getId())
                .title(product.getTitle())
                .description(product.getDescription())
                .price(product.getPrice())
                .image(product.getImage())
                .rating(product.getRating())
                .reviewCount(product.getReviewCount())
                .stock(product.getStock())
                .slug(product.getSlug())
                .brand(product.getBrand())
                .compareAtPrice(product.getCompareAtPrice())
                .category(product.getCategory().getName())
                .categoryId(product.getCategory().getId())
                .categorySlug(product.getCategory().getSlug())
                .material(product.getMaterial())
                .careInstructions(product.getCareInstructions())
                .gender(product.getGender())
                .isActive(product.getIsActive())
                .variants(mapToGetVariantResponseDtoList(variants, product.getPrice()))
                .images(mapToGetProductImageResponseDtoList(images))
                .build();
    }

    // priceDelta is relative, so the sellable price only exists once the parent price is known.
    public GetVariantResponseDto mapToGetVariantResponseDto(ProductVariant variant, BigDecimal productPrice) {
        BigDecimal delta = variant.getPriceDelta() != null ? variant.getPriceDelta() : BigDecimal.ZERO;
        return GetVariantResponseDto.builder()
                .id(variant.getId())
                .productId(resolveProductId(variant))
                .sku(variant.getSku())
                .size(variant.getSize())
                .colorName(variant.getColorName())
                .colorHex(variant.getColorHex())
                .priceDelta(delta)
                .imageUrl(variant.getImageUrl())
                .stock(variant.getStock())
                .price(productPrice != null ? productPrice.add(delta) : null)
                .build();
    }

    /**
     * product_id is an insertable=false view of the association, so it is only guaranteed on a
     * row Hibernate has loaded. A variant saved moments ago still carries the association, and
     * reading the identifier off that proxy does not trigger a load.
     */
    private static Long resolveProductId(ProductVariant variant) {
        if (variant.getProductId() != null) {
            return variant.getProductId();
        }
        return variant.getProduct() != null ? variant.getProduct().getId() : null;
    }

    public List<GetVariantResponseDto> mapToGetVariantResponseDtoList(List<ProductVariant> variants,
                                                                     BigDecimal productPrice) {
        return variants.stream()
                .map(variant -> mapToGetVariantResponseDto(variant, productPrice))
                .collect(Collectors.toList());
    }

    public GetProductImageResponseDto mapToGetProductImageResponseDto(ProductImage image) {
        return GetProductImageResponseDto.builder()
                .id(image.getId())
                .url(image.getUrl())
                .position(image.getPosition())
                .alt(image.getAlt())
                .build();
    }

    public List<GetProductImageResponseDto> mapToGetProductImageResponseDtoList(List<ProductImage> images) {
        return images.stream().map(this::mapToGetProductImageResponseDto).collect(Collectors.toList());
    }
}
