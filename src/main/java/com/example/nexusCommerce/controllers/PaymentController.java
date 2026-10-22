package com.example.nexusCommerce.controllers;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.nexusCommerce.dtos.CreatePaymentResponseDto;
import com.example.nexusCommerce.dtos.GetPaymentStatusResponseDto;
import com.example.nexusCommerce.dtos.VerifyPaymentRequestDto;
import com.example.nexusCommerce.security.AuthenticatedUser;
import com.example.nexusCommerce.services.PaymentService;
import com.example.nexusCommerce.utils.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Payments", description = "Razorpay checkout for an existing order")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Start payment for an order",
               description = "Creates the Razorpay order for the caller's order total and returns the "
                             + "checkout ticket (key id, Razorpay order id and amount in paise). "
                             + "Repeat calls re-price the order and replace any earlier ticket.")
    @PostMapping("/razorpay/orders/{orderId}")
    public ResponseEntity<ApiResponse<CreatePaymentResponseDto>> createPayment(
            @PathVariable Long orderId,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.createPayment(caller.id(), orderId), "Payment initiated"));
    }

    @Operation(summary = "Confirm a completed checkout",
               description = "Verifies the Razorpay signature over the payment and marks the order paid. "
                             + "Safe to call twice: an already-paid order returns its current status.")
    @PostMapping("/razorpay/orders/{orderId}/verify")
    public ResponseEntity<ApiResponse<GetPaymentStatusResponseDto>> verifyPayment(
            @PathVariable Long orderId,
            @Valid @RequestBody VerifyPaymentRequestDto requestDto,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.verifyPayment(caller.id(), orderId, requestDto), "Payment verified"));
    }

    @Operation(summary = "Get payment status of an order",
               description = "Owner-only. The storefront polls this after the checkout sheet closes, "
                             + "because the gateway webhook may settle the order first.")
    @GetMapping("/orders/{orderId}/status")
    public ResponseEntity<ApiResponse<GetPaymentStatusResponseDto>> paymentStatus(
            @PathVariable Long orderId,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.paymentStatus(caller.id(), orderId), "Payment status fetched"));
    }

    /**
     * Razorpay posts here with no bearer token -- it is authenticated by its signature over
     * the exact bytes of the body, which is why the payload is taken as byte[] rather than a
     * bound DTO (parsing it first would lose the bytes that were signed).
     */
    @Operation(summary = "Razorpay webhook",
               description = "Server-to-server payment events. Requires no user token; verified by the "
                             + "X-Razorpay-Signature HMAC (a missing or forged signature is a caller-auth "
                             + "failure and answers 401) and configured to return 2xx even for unknown "
                             + "orders so the gateway stops retrying.")
    @PostMapping("/razorpay/webhook")
    public ResponseEntity<ApiResponse<Map<String, Object>>> webhook(
            @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature,
            @RequestBody byte[] rawBody) {
        paymentService.handleWebhook(rawBody, signature);
        return ResponseEntity.ok(ApiResponse.success(Map.of("received", true), "Webhook processed"));
    }
}
