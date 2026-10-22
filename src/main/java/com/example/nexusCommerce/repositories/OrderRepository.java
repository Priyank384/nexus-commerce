package com.example.nexusCommerce.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.nexusCommerce.schema.Order;
import com.example.nexusCommerce.schema.OrderStatus;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByUser_Id(Long userId, Pageable pageable);

    /**
     * Resolves a gateway webhook (which arrives with no user context) back to our order.
     * Matches only the intent we issued, so a forged reference can only ever name an order
     * that actually exists here.
     */
    Optional<Order> findByPaymentIntentId(String paymentIntentId);

    long countByStatus(OrderStatus status);

    // Live orders only (@SQLRestriction is applied); grouped for the admin dashboard.
    @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
    List<Object[]> countGroupedByStatus();
}
