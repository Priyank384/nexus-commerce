package com.example.nexusCommerce.repositories;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.nexusCommerce.schema.Order;
import com.example.nexusCommerce.schema.OrderProducts;

@Repository
public interface  OrderProductsRepository extends JpaRepository<OrderProducts, Long>{


    boolean existsByOrderIdAndProduct_Id(Long orderId, Long productId);

    // No JOIN FETCH op.product: the line item is rendered entirely from its own snapshot
    // columns, and a fetch join would silently drop items whose product was soft-deleted.
    @Query("SELECT op FROM OrderProducts op WHERE op.order = :order")
    List<OrderProducts> findByOrderWithProduct(Order order);

    /**
     * Loads every line item for a whole page of orders in one statement so callers can
     * group them in memory instead of paying 1 + N round trips per request.
     */
    @Query("SELECT op FROM OrderProducts op WHERE op.order.id IN :orderIds")
    List<OrderProducts> findByOrderIdsWithProduct(@Param("orderIds") List<Long> orderIds);

    @Query("SELECT COALESCE(SUM(op.unitPrice * op.quantity), 0) FROM OrderProducts op")
    BigDecimal totalRevenue();

    /**
     * Returns (variantId, productId, quantity) triples as scalars. Used to hand stock back
     * when an order is deleted without loading the OrderProducts entities into the persistence
     * context -- managed line items left referencing a soft-deleted Order make the commit
     * flush fail with a TransientPropertyValueException. variant_id is null only on pre-V20
     * rows, and productId is carried alongside so those legacy lines can still be refunded to
     * the product-level counter.
     */
    @Query("SELECT op.variant.id, op.product.id, op.quantity FROM OrderProducts op WHERE op.order.id = :orderId")
    List<Object[]> findStockDeltasByOrderId(@Param("orderId") Long orderId);

    /**
     * Soft-deletes every live line item of an order in one statement. Without this the
     * OrderProducts rows survive (still pointing at a soft-deleted Order), keep counting
     * toward admin revenue, and block stock accounting from ever reconciling.
     */
    @Modifying
    @Query("UPDATE OrderProducts op SET op.deletedAt = CURRENT_TIMESTAMP "
            + "WHERE op.order.id = :orderId AND op.deletedAt IS NULL")
    int softDeleteByOrderId(@Param("orderId") Long orderId);
}

