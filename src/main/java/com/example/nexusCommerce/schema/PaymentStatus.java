package com.example.nexusCommerce.schema;

/**
 * Where an order is in its payment lifecycle, kept separate from {@link OrderStatus}
 * because they answer different questions: {@code status} is fulfilment (what the warehouse
 * owes the shopper), {@code paymentStatus} is money (what the shopper owes us). A PENDING
 * order can be paid or abandoned, and a DELIVERED order was paid long before it shipped.
 */
public enum PaymentStatus {
    CREATED,
    PAID,
    FAILED
}
