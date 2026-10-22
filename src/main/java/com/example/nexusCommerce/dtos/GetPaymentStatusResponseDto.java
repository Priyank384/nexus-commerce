package com.example.nexusCommerce.dtos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.nexusCommerce.schema.OrderStatus;
import com.example.nexusCommerce.schema.PaymentStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The payment answer the storefront polls after Checkout closes. Its own DTO rather than
 * {@link GetOrderResponseDto} because the shopper needs the gateway references (and the reason
 * a payment failed) while the order response deliberately stays a fulfilment view.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GetPaymentStatusResponseDto {

    private Long orderId;
    private OrderStatus orderStatus;
    private PaymentStatus paymentStatus;
    private BigDecimal totalPrice;
    private String currency;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private LocalDateTime paidAt;
}
