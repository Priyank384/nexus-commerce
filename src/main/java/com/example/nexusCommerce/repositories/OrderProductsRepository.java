package com.example.nexusCommerce.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.nexusCommerce.schema.OrderProducts;

@Repository
public interface  OrderProductsRepository extends JpaRepository<OrderProducts, Long>{

    List<OrderProducts> findByOrderId(Long orderId);
}
