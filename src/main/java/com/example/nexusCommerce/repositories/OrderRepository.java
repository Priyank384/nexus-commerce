package com.example.nexusCommerce.repositories;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.nexusCommerce.schema.Order;
import com.example.nexusCommerce.schema.OrderStatus;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByUser_Id(Long userId, Pageable pageable);

    long countByStatus(OrderStatus status);

    // Live orders only (@SQLRestriction is applied); grouped for the admin dashboard.
    @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
    List<Object[]> countGroupedByStatus();
}
