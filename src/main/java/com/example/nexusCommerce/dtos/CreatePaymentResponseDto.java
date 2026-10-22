package com.example.nexusCommerce.dtos;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Everything Razorpay Checkout needs to open the payment sheet. The shopper's browser talks
 * to Razorpay directly, so card details never reach this API -- which is the only reason this
 * app is not a PCI DSS scope problem.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePaymentResponseDto {

    private Long orderId;
    private String razorpayOrderId;
    private String keyId;
    private String currency;
    /** Rupee total, the figure to show the shopper. */
    private BigDecimal totalPrice;
    /**
     * Minor-unit (paise) amount. Razorpay only accepts that unit, so it is handed straight to
     * Checkout; keeping both fields avoids every client doing its own x100 arithmetic.
     */
    private long amount;
}
