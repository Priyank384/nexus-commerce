package com.example.nexusCommerce.schema;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    // Payment lifecycle. provider/intent/ref are Strings rather than enums/relations because
    // the gateway is a swap-able external system: the shape of its identifiers is not ours to
    // constrain, and adding a payments table would only be worth it once we have to reconcile
    // several partial captures per order. A single full capture per order is all the storefront
    // does today.
    @Column(name = "payment_provider", length = 30)
    private String paymentProvider;

    // The reference the gateway issued for us, before any money moved. The webhook and the
    // signature check both match on this, so it is the one value that must survive the
    // shopper completing checkout.
    @Column(name = "payment_intent_id", length = 120)
    private String paymentIntentId;

    // The amount the stored intent was priced at, in paise. Without it a repeat "start payment"
    // cannot tell whether the intent it already issued still matches the order, and re-issuing
    // on a mismatch would orphan the intent a shopper may still be paying.
    @Column(name = "payment_amount_paise")
    private Long paymentAmountPaise;

    // The gateway's own payment id, only known once the capture succeeded.
    @Column(name = "payment_ref", length = 120)
    private String paymentRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 20)
    private PaymentStatus paymentStatus;

    @Column(name = "paid_datetime")
    private LocalDateTime paidAt;

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
