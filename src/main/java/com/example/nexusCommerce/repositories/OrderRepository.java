package com.example.nexusCommerce.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.nexusCommerce.schema.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {
    
}
