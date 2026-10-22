package com.example.nexusCommerce.services;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.ProductVariantRepository;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.schema.ProductVariant;

import lombok.RequiredArgsConstructor;

/**
 * Cart lines and order lines are variant-scoped since V22, so every purchase resolves to
 * exactly one size+colour. This is the single place that decision is made, because both the
 * cart and the order pipeline need it and they must agree on what "no variant given" means.
 */
@Component
@RequiredArgsConstructor
public class VariantResolver {

    private final ProductVariantRepository variantRepository;

    /**
     * A caller that names a variant gets that variant, after checking it belongs to the
     * product. A caller that does not gets the product's only variant, which is what V16's
     * LEGACY backfill guarantees for pre-apparel rows; anything else is rejected rather than
     * silently guessed, because guessing a size is how wrong garments get shipped.
     */
    public ProductVariant resolve(Product product, Long variantId) {
        if (variantId != null) {
            ProductVariant variant = variantRepository.findById(variantId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Variant with id: " + variantId + " Not Found!"));
            if (!product.getId().equals(resolveProductId(variant))) {
                throw new InvalidRequestException("Variant " + variantId
                        + " does not belong to product " + product.getId());
            }
            return variant;
        }

        if (variantRepository.countByProductId(product.getId()) != 1L) {
            throw new InvalidRequestException("Product " + product.getId() + " ("
                    + product.getTitle() + ") is sold in multiple variants; a variantId (size + colour)"
                    + " is required");
        }
        return variantRepository.findFirstByProductIdOrderByCreatedAtAsc(product.getId())
                .orElseThrow(() -> new InvalidRequestException(
                        "Product " + product.getId() + " has no purchasable variant"));
    }

    /**
     * The variant's delta is relative to the parent price, so the sellable price only exists
     * once both rows are in hand. Snapshotted onto the line at purchase time.
     */
    public BigDecimal unitPrice(Product product, ProductVariant variant) {
        BigDecimal delta = variant.getPriceDelta() == null ? BigDecimal.ZERO : variant.getPriceDelta();
        return product.getPrice().add(delta);
    }

    // A just-saved variant has no read-only product_id mirror, so fall back to the association.
    private static Long resolveProductId(ProductVariant variant) {
        if (variant.getProductId() != null) {
            return variant.getProductId();
        }
        return variant.getProduct() != null ? variant.getProduct().getId() : null;
    }
}
