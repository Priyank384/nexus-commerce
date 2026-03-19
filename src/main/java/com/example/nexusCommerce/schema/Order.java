package com.example.nexusCommerce.schema;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name="orders")
@SQLDelete(sql = "UPDATE orders SET deleted_datetime = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_datetime is NULL")
public class Order extends BaseEntity{
    
    private OrderStatus status;

    // @ManyToMany
    // @JoinTable(
    //     name = "order_products",
    //     joinColumns=@JoinColumn(name="order_id"),
    //     inverseJoinColumns=@JoinColumn(name="product_id")
    // )
    // private List<Product> products;
}
