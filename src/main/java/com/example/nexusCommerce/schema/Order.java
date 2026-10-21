package com.example.nexusCommerce.schema;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Version;
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
@Table(name="orders")
@SQLDelete(sql = "UPDATE orders SET deleted_datetime = CURRENT_TIMESTAMP, version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_datetime is NULL")
public class Order extends BaseEntity{
    
    @ManyToOne (fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private OrderStatus status;

    @Version
    @Column(nullable = false)
    private Long version;

    // @ManyToMany
    // @JoinTable(
    //     name = "order_products",
    //     joinColumns=@JoinColumn(name="order_id"),
    //     inverseJoinColumns=@JoinColumn(name="product_id")
    // )
    // private List<Product> products;
}
