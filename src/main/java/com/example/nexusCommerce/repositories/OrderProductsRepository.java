package com.example.nexusCommerce.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.nexusCommerce.schema.Order;
import com.example.nexusCommerce.schema.OrderProducts;

@Repository
public interface  OrderProductsRepository extends JpaRepository<OrderProducts, Long>{

    List<OrderProducts> findByOrderId(Long orderId);

    @Query("SELECT OP FROM OrderProducts op JOIN FETCH op.product WHERE op.order = :order")
    List<OrderProducts> findByOrderWithProduct(Order order);
}
