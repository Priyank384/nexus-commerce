package com.example.nexusCommerce.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.nexusCommerce.schema.Order;
import com.example.nexusCommerce.schema.OrderStatus;
import com.example.nexusCommerce.schema.PaymentStatus;

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

    /**
     * Moves an order to {@code to} only while it is not already {@code settled}. A client verify
     * and Razorpay's webhook routinely race for the same order, and read-then-save would let both
     * win: two confirmation mails for one payment. Same conditional-write contract as the stock
     * decrements, so exactly one caller gets a row count of 1 and only that caller notifies.
     *
     * <p>The version is bumped by hand and the soft-delete guard is spelled out because a bulk
     * statement bypasses both {@code @Version} and {@code @SQLRestriction}.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Order o SET o.paymentStatus = :to, o.paymentProvider = :provider, "
            + "o.paymentRef = :paymentRef, o.paidAt = :paidAt, o.version = o.version + 1 "
            + "WHERE o.id = :id AND o.deletedAt IS NULL "
            + "AND (o.paymentStatus IS NULL OR o.paymentStatus <> :settled)")
    int settleIfUnpaid(@Param("id") Long id, @Param("provider") String provider,
            @Param("paymentRef") String paymentRef, @Param("paidAt") LocalDateTime paidAt,
            @Param("to") PaymentStatus to, @Param("settled") PaymentStatus settled);

    /**
     * Records a failed attempt without ever undoing a capture: Razorpay can report a failure for
     * one instrument on an order another instrument already paid, and that must not reopen an
     * order the money has settled. Only the status moves, so a payment reference from an earlier
     * attempt survives.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Order o SET o.paymentStatus = :to, o.version = o.version + 1 "
            + "WHERE o.id = :id AND o.deletedAt IS NULL "
            + "AND (o.paymentStatus IS NULL OR o.paymentStatus <> :settled)")
    int markFailedIfNotPaid(@Param("id") Long id, @Param("to") PaymentStatus to,
            @Param("settled") PaymentStatus settled);
}
