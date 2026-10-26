package com.example.nexusCommerce.services;

import static com.example.nexusCommerce.configs.CacheConfig.ORDER_ALL;
import static com.example.nexusCommerce.configs.CacheConfig.ORDER_BY_ID;
import static com.example.nexusCommerce.configs.CacheConfig.ORDER_SUMMARY;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.nexusCommerce.dtos.CreatePaymentResponseDto;
import com.example.nexusCommerce.dtos.GetPaymentStatusResponseDto;
import com.example.nexusCommerce.dtos.VerifyPaymentRequestDto;
import com.example.nexusCommerce.exceptions.ForbiddenException;
import com.example.nexusCommerce.exceptions.InvalidCredentialsException;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.OrderProductsRepository;
import com.example.nexusCommerce.repositories.OrderRepository;
import com.example.nexusCommerce.schema.Order;
import com.example.nexusCommerce.schema.PaymentStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Razorpay-backed {@link PaymentService}. The state machine is deliberately conservative: the
 * order total is priced at the moment an intent is issued, a payment only settles when its
 * signature matches the intent this server handed out, and anything already PAID is never
 * re-settled, so a retried verify or a duplicated webhook cannot double-charge or double-mail.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RazorpayPaymentServiceImpl implements PaymentService {

    private static final String PROVIDER = "razorpay";
    /** Razorpay rejects an order below one rupee; a zero-line order must not reach them. */
    private static final long MIN_AMOUNT_IN_PAISE = 100;

    private final OrderRepository orderRepository;
    private final OrderProductsRepository orderProductsRepository;
    private final RazorpayClient razorpay;
    private final NotificationService notifications;
    /** A bulk UPDATE needs a live transaction; createPayment has none of its own by design. */
    private final TransactionTemplate transactionTemplate;

    /**
     * Deliberately not {@code @Transactional}: issuing an intent is an HTTP round trip, and
     * holding a pooled connection (plus the order row lock) for its duration turns a slow
     * provider into database saturation. The intent is recorded afterwards with a conditional
     * UPDATE that refuses to overwrite a capture that won the race meanwhile.
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = ORDER_ALL, allEntries = true),
            @CacheEvict(cacheNames = { ORDER_BY_ID, ORDER_SUMMARY }, key = "#orderId")
    })
    @Override
    public CreatePaymentResponseDto createPayment(Long userId, Long orderId) {
        Order order = requireOwned(userId, orderId);
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new InvalidRequestException("Order " + orderId + " has already been paid");
        }
        BigDecimal totalPrice = totalOf(orderId);
        long amountInPaise = toPaise(totalPrice);
        // Reuse the intent we already issued while it still prices the same order. Issuing a
        // fresh one on every call would leave the previous intent live at Razorpay but
        // unreachable from here, so paying it would capture money against an order we can no
        // longer resolve. A genuine re-price is the one case that does need a new intent.
        boolean pricedTheSame = order.getPaymentAmountPaise() != null
                && order.getPaymentAmountPaise() == amountInPaise
                && order.getPaymentStatus() == PaymentStatus.CREATED;
        String razorpayOrderId;
        if (pricedTheSame) {
            razorpayOrderId = order.getPaymentIntentId();
        } else {
            razorpayOrderId = razorpay.createOrder(amountInPaise, "order-" + orderId, orderId.toString());
            Integer recorded = transactionTemplate.execute(status ->
                    orderRepository.recordIntentIfUnpaid(orderId, PROVIDER, razorpayOrderId,
                            amountInPaise, PaymentStatus.CREATED, PaymentStatus.PAID));
            if (recorded == null || recorded == 0) {
                // A verify/webhook settled the order while Razorpay was creating this intent.
                // This order is already paid, so the new intent must not become the current one.
                throw new InvalidRequestException("Order " + orderId + " has already been paid");
            }
        }

        return CreatePaymentResponseDto.builder()
                .orderId(orderId)
                .razorpayOrderId(razorpayOrderId)
                .keyId(razorpay.keyId())
                .currency(razorpay.currency())
                .totalPrice(totalPrice)
                .amount(amountInPaise)
                .build();
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = ORDER_ALL, allEntries = true),
            @CacheEvict(cacheNames = { ORDER_BY_ID, ORDER_SUMMARY }, key = "#orderId")
    })
    @Override
    public GetPaymentStatusResponseDto verifyPayment(Long userId, Long orderId, VerifyPaymentRequestDto requestDto) {
        Order order = requireOwned(userId, orderId);
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            // Replay-safe: the storefront retries this after a page refresh, and a second
            // success must not send a second confirmation.
            return statusOf(order);
        }
        if (order.getPaymentIntentId() == null) {
            throw new InvalidRequestException("No payment has been started for order " + orderId);
        }
        // Signed over the intent stored here, never over the id the client sends, so the
        // payload cannot point at a payment made against somebody else's order.
        String signedPayload = order.getPaymentIntentId() + "|" + requestDto.getRazorpayPaymentId();
        if (!razorpay.paymentSignatureMatches(signedPayload, requestDto.getRazorpaySignature())) {
            throw new InvalidRequestException(
                    "Payment signature did not match for order " + orderId + "; nothing was captured");
        }
        return statusOf(settle(orderId, requestDto.getRazorpayPaymentId()));
    }

    @Transactional(readOnly = true)
    @Override
    public GetPaymentStatusResponseDto paymentStatus(Long userId, Long orderId) {
        return statusOf(requireOwned(userId, orderId));
    }

    /**
     * Razorpay's own confirmation, which is what makes a paid order safe even if the browser
     * never reaches our verify endpoint. Evicts by {@code allEntries} because the order id is
     * discovered from the payload, so no request parameter exists to key a single eviction on.
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = { ORDER_ALL, ORDER_BY_ID, ORDER_SUMMARY }, allEntries = true)
    })
    @Override
    public void handleWebhook(byte[] rawBody, String signatureHeader) {
        if (!razorpay.webhookSignatureMatches(rawBody, signatureHeader)) {
            throw new InvalidCredentialsException("Razorpay webhook signature did not match");
        }
        RazorpayClient.WebhookEvent event = razorpay.parseWebhook(rawBody);
        if (event.razorpayOrderId() == null) {
            log.info("Ignoring Razorpay webhook event '{}' without an order reference", event.event());
            return;
        }
        Order order = orderRepository.findByPaymentIntentId(event.razorpayOrderId()).orElse(null);
        if (order == null) {
            // Deliberately not a 404: Razorpay retries non-2xx forever, and an order from
            // another environment's key is never going to show up here.
            log.warn("Razorpay event '{}' names unknown payment intent {}", event.event(), event.razorpayOrderId());
            return;
        }
        switch (event.event() == null ? "" : event.event()) {
            case "payment.captured", "order.paid" -> {
                if (order.getPaymentStatus() != PaymentStatus.PAID) {
                    settle(order.getId(), event.razorpayPaymentId());
                }
            }
            case "payment.failed", "payment.process_failed" -> {
                // Status only: a failure for one instrument must not reopen an order another
                // instrument already paid, and must not drop the reference from a good capture.
                orderRepository.markFailedIfNotPaid(order.getId(), PaymentStatus.FAILED, PaymentStatus.PAID);
            }
            default -> log.info("Ignoring Razorpay event '{}' for order {}", event.event(), order.getId());
        }
    }

    /**
     * Writes the capture and notifies, allowing exactly one winner. The client's verify and
     * Razorpay's webhook arrive together routinely, and a read-then-save would let both settle and
     * both mail. Returns the settled row, re-read because the bulk update cleared the persistence
     * context.
     */
    private Order settle(Long orderId, String razorpayPaymentId) {
        int settled = orderRepository.settleIfUnpaid(orderId, PROVIDER, razorpayPaymentId,
                LocalDateTime.now(), PaymentStatus.PAID, PaymentStatus.PAID);
        Order paid = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id: " + orderId + " Not Found!"));
        if (settled == 1) {
            notifications.sendOrderConfirmation(paid.getUser().getId(), paid.getId(), paid.getUser().getEmail());
            log.info("Order {} settled by {} (payment {})", orderId, PROVIDER, razorpayPaymentId);
        } else {
            log.info("Order {} was settled by a concurrent request; confirmation already sent", orderId);
        }
        return paid;
    }

    private Order requireOwned(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id: " + orderId + " Not Found!"));
        if (!order.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Order " + orderId + " does not belong to user " + userId);
        }
        return order;
    }

    private GetPaymentStatusResponseDto statusOf(Order order) {
        return GetPaymentStatusResponseDto.builder()
                .orderId(order.getId())
                .orderStatus(order.getStatus())
                .paymentStatus(order.getPaymentStatus())
                .totalPrice(totalOf(order.getId()))
                .currency(razorpay.currency())
                .razorpayOrderId(order.getPaymentIntentId())
                .razorpayPaymentId(order.getPaymentRef())
                .paidAt(order.getPaidAt())
                .build();
    }

    private BigDecimal totalOf(Long orderId) {
        return orderProductsRepository.totalByOrderId(orderId);
    }

    /**
     * {@code unit_price} is NUMERIC(38,2), so shifting the decimal twice is exact; anything
     * that is not represents an order total beyond a long, which is not a real checkout.
     */
    private static long toPaise(BigDecimal rupees) {
        long paise = rupees.movePointRight(2).longValueExact();
        if (paise < MIN_AMOUNT_IN_PAISE) {
            throw new InvalidRequestException("This order has nothing to pay for");
        }
        return paise;
    }
}
