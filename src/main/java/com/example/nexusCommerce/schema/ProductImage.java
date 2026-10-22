package com.example.nexusCommerce.schema;

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
 * Ordered product gallery image. Position 0 is the primary/thumbnail; V21 seeds positions
 * 0-3 per product. {@code products.image} still mirrors position 0 for historic readers.
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "product_images", indexes = {
    @Index(name = "idx_pi_product_position", columnList = "product_id,position"),
})
@SQLDelete(sql = "UPDATE product_images SET deleted_datetime = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_datetime is NULL")
public class ProductImage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_id", insertable = false, updatable = false, nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    @Builder.Default
    private Integer position = 0;

    @Column(length = 255)
    private String alt;
}
