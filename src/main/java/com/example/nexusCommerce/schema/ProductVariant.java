package com.example.nexusCommerce.schema;

import java.math.BigDecimal;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single purchasable size+colour combination under a product. Stock, price delta and
 * the swatch image all live here. Historic products (pre-V16) get a synthetic ONE / Default
 * variant so their orders and cart lines keep resolving.
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "product_variants", indexes = {
    @Index(name = "idx_pv_product", columnList = "product_id"),
    @Index(name = "idx_pv_size", columnList = "size"),
    @Index(name = "idx_pv_color", columnList = "color_name"),
})
@SQLDelete(sql = "UPDATE product_variants SET deleted_datetime = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_datetime is NULL")
public class ProductVariant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_id", insertable = false, updatable = false, nullable = false)
    private Long productId;

    @Column(nullable = false, length = 64)
    private String sku;

    /** XS / S / M / L / XL / XXL for apparel, ONE for legacy rows. */
    @Column(nullable = false, length = 20)
    private String size;

    @Column(name = "color_name", nullable = false, length = 60)
    private String colorName;

    /** #RRGGBB swatch for the storefront. */
    @Column(name = "color_hex", nullable = false, length = 9)
    private String colorHex;

    @Column(name = "price_delta", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal priceDelta = BigDecimal.ZERO;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(nullable = false)
    @Builder.Default
    private Integer stock = 0;
}
