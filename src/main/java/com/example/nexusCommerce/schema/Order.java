package com.example.nexusCommerce.schema;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

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
    
    /**
     * Null on a guest order (V29). Readers must not dereference it blindly: use
     * {@link #recipientEmail()} for mail and the identity helpers for ownership.
     */
    @ManyToOne (fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** Where the confirmation mail goes for an order placed without an account. */
    @Column(name = "guest_email")
    private String guestEmail;

    // The session that placed a guest order. It is the credential that may price, verify and
    // poll that order's payment, which is how a shopper finishes Razorpay with no token at all.
    // Cleared when the order is claimed, since the account owns it from then on.
    @Column(name = "guest_token", length = 64)
    private String guestToken;

    // Separate from guestToken because it travels in an email: the session token also unlocks
    // the cart and every other order of that browser, so only this single-order secret may be
    // forwarded or opened on another device. Also cleared on claim, which retires the link.
    @Column(name = "claim_token", length = 64)
    private String claimToken;

    // V20's JSON column, written once at checkout and never touched again.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "address_snapshot")
    private AddressSnapshot addressSnapshot;

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

    /** The address a receipt goes to: the account's email, or what the guest typed at checkout. */
    public String recipientEmail() {
        return user != null ? user.getEmail() : guestEmail;
    }

    public boolean ownedByUser(Long userId) {
        return userId != null && user != null && userId.equals(user.getId());
    }

    public boolean ownedByGuestToken(String token) {
        return token != null && token.equals(guestToken);
    }

    // @ManyToMany
    // @JoinTable(
    //     name = "order_products",
    //     joinColumns=@JoinColumn(name="order_id"),
    //     inverseJoinColumns=@JoinColumn(name="product_id")
    // )
    // private List<Product> products;
}
