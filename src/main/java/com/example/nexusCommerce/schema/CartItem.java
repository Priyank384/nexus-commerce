package com.example.nexusCommerce.schema;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cart_items")
@SQLDelete(sql = "UPDATE cart_items SET deleted_datetime = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_datetime is NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // Read-only mirror of the FK so callers (and the DTO mapper) can see the id without
    // triggering a lazy load on Product.
    @Column(name = "product_id", insertable = false, updatable = false, nullable = false)
    private Long productId;

    /**
     * Variant the shopper picked. Nullable through Phase 2a: V20 backfills historic rows
     * to the synthetic ONE / Default variant, and the DB-level NOT NULL + (cart, variant)
     * uniqueness flip ships with Phase 4's variant-aware CartService.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @Column(name = "variant_id", insertable = false, updatable = false)
    private Long variantId;

    @Column(nullable = false)
    private Integer quantity;
}
