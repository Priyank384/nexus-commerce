package com.example.nexusCommerce.services;

import com.example.nexusCommerce.dtos.CreatePaymentResponseDto;
import com.example.nexusCommerce.dtos.GetPaymentStatusResponseDto;
import com.example.nexusCommerce.dtos.VerifyPaymentRequestDto;

/**
 * The payment seam. Everything above it (order state, ownership, caching, confirmation mail)
 * is provider-neutral, so swapping Razorpay for another gateway replaces one implementation
 * and no controller.
 */
public interface PaymentService {

    /** Issue a gateway order for the order's current total and hand back the checkout ticket. */
    CreatePaymentResponseDto createPayment(Long userId, Long orderId);

    /** Prove the shopper's completed checkout really came from the gateway, then settle the order. */
    GetPaymentStatusResponseDto verifyPayment(Long userId, Long orderId, VerifyPaymentRequestDto requestDto);

    GetPaymentStatusResponseDto paymentStatus(Long userId, Long orderId);

    /** Gateway-initiated settlement, with no shopper or token attached -- verified by signature alone. */
    void handleWebhook(byte[] rawBody, String signatureHeader);
}
