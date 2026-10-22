package com.example.nexusCommerce.schema;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_products_slug", columnList = "slug"),
    @Index(name = "idx_products_gender", columnList = "gender"),
    @Index(name = "idx_products_is_active", columnList = "is_active"),
})
@SQLDelete(sql = "UPDATE products SET deleted_datetime = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_datetime is NULL")
public class Product extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "compare_at_price", precision = 38, scale = 2)
    private BigDecimal compareAtPrice;

    private String image;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, precision = 3, scale = 1)
    private BigDecimal rating;

    /**
     * Legacy aggregate stock. Kept for backward compatibility with historic order
     * snapshots; the truth lives at the variant level (see {@link #variants}).
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer stock = 0;

    @Column(length = 120)
    private String brand;

    @Column(length = 255)
    private String material;

    @Column(name = "care_instructions", length = 500)
    private String careInstructions;

    /** MEN / WOMEN / UNISEX / KIDS -- nullable so historic rows keep working. */
    @Column(length = 10)
    private String gender;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = Boolean.TRUE;

    @Column(length = 120)
    private String slug;

    @Builder.Default
    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    private List<ProductVariant> variants = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    private List<ProductImage> images = new ArrayList<>();
}
