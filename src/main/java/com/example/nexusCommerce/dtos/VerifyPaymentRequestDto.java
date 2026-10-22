package com.example.nexusCommerce.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What Razorpay Checkout hands back after the shopper authorises, posted to us so the server
 * can prove it really came from Razorpay.
 *
 * <p>The Razorpay order id is deliberately absent: the signature is checked against the intent
 * this server issued for the order, so a client cannot substitute a payment made against some
 * other (or replayed) order.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class VerifyPaymentRequestDto {

    @NotBlank
    private String razorpayPaymentId;

    @NotBlank
    private String razorpaySignature;
}
