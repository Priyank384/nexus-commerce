package com.example.nexusCommerce.schema;

import java.math.BigDecimal;

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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name="order_products")
@SQLDelete(sql = "UPDATE order_products SET deleted_datetime = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_datetime is NULL")
public class OrderProducts extends BaseEntity{
    
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="order_id", nullable=false)
    private Order order;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="product_id", nullable=false)
    private Product product;

    // Read-only view of the FK so the line item can be rendered even when the product row
    // is hidden by Product's soft-delete restriction.
    @Column(name = "product_id", insertable = false, updatable = false, nullable = false)
    private Long productId;

    @Column(nullable=false)
    private Integer quantity;

    // Price paid at the moment the line was reserved. Revenue and past order totals must not
    // change when an admin later edits the catalogue price.
    @Column(nullable = false)
    private BigDecimal unitPrice;

    // Name/image snapshotted at purchase time, so editing or soft-deleting the product later
    // cannot rewrite or drop the line item from a historical order.
    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "product_image")
    private String productImage;
}
