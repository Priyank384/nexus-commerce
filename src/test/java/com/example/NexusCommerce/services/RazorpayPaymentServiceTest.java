package com.example.nexusCommerce.services;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import com.example.nexusCommerce.schema.OrderStatus;
import com.example.nexusCommerce.schema.PaymentStatus;
import com.example.nexusCommerce.schema.User;

@ExtendWith(MockitoExtension.class)
class RazorpayPaymentServiceTest {

    private static final byte[] WEBHOOK_BODY =
            "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderProductsRepository orderProductsRepository;

    @Mock
    private RazorpayClient razorpay;

    @Mock
    private NotificationService notifications;

    @InjectMocks
    private RazorpayPaymentServiceImpl paymentService;

    private Order unpaidOrder(Long ownerId, String intentId, PaymentStatus status) {
        Order order = Order.builder()
                .status(OrderStatus.PENDING)
                .paymentIntentId(intentId)
                .paymentStatus(status)
                .build();
        order.setId(7L);
        User owner = User.builder().name("Shopper").email("shopper@example.com").build();
        owner.setId(ownerId);
        order.setUser(owner);
        return order;
    }

    private void givenOrder(Order order) {
        lenient().when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
    }

    private void givenTotal(String rupees) {
        lenient().when(orderProductsRepository.totalByOrderId(7L)).thenReturn(new BigDecimal(rupees));
    }

    /**
     * The settle is a conditional UPDATE, not a save, so the fixture has to move the way the row
     * would. Returning 1 means "this caller won the race"; the service re-reads the order after
     * the bulk write, which hands back this same instance.
     */
    private void givenSettleWins(Order order) {
        lenient().when(orderRepository.settleIfUnpaid(eq(7L), any(), any(), any(),
                eq(PaymentStatus.PAID), eq(PaymentStatus.PAID)))
                .thenAnswer(call -> {
                    order.setPaymentStatus(PaymentStatus.PAID);
                    order.setPaymentProvider("razorpay");
                    order.setPaymentRef(call.getArgument(2));
                    order.setPaidAt(java.time.LocalDateTime.now());
                    return 1;
                });
    }

    /**
     * Stands in for a capture that landed while this request was in flight: the guard matches no
     * row, so this caller writes nothing, but the row the service re-reads is already settled.
     */
    private void givenSettleLoses(Order order) {
        lenient().when(orderRepository.settleIfUnpaid(eq(7L), any(), any(), any(),
                eq(PaymentStatus.PAID), eq(PaymentStatus.PAID)))
                .thenAnswer(call -> {
                    order.setPaymentStatus(PaymentStatus.PAID);
                    return 0;
                });
    }

    @Test
    void createPayment_pricesTheOrderAndStoresTheIssuedIntent() {
        Order order = unpaidOrder(1L, null, null);
        givenOrder(order);
        givenTotal("1299.00");
        when(razorpay.createOrder(129900L, "order-7", "7")).thenReturn("order_RZP1");
        when(razorpay.keyId()).thenReturn("rzp_test_123");
        when(razorpay.currency()).thenReturn("INR");

        CreatePaymentResponseDto result = paymentService.createPayment(1L, 7L);

        assertEquals("order_RZP1", result.getRazorpayOrderId());
        assertEquals(129900L, result.getAmount());
        assertEquals(new BigDecimal("1299.00"), result.getTotalPrice());
        assertEquals("rzp_test_123", result.getKeyId());
        assertEquals("razorpay", order.getPaymentProvider());
        assertEquals("order_RZP1", order.getPaymentIntentId());
        assertEquals(PaymentStatus.CREATED, order.getPaymentStatus());
        verify(orderRepository).save(order);
    }

    @Test
    void createPayment_whenOrderAlreadyPaid_neverCallsTheGateway() {
        Order order = unpaidOrder(1L, "order_RZP1", PaymentStatus.PAID);
        givenOrder(order);

        assertThrows(InvalidRequestException.class, () -> paymentService.createPayment(1L, 7L));
        verify(razorpay, never()).createOrder(anyLong(), anyString(), anyString());
    }

    @Test
    void createPayment_whenOrderBelongsToAnotherUser_isRejected() {
        Order order = unpaidOrder(99L, null, null);
        givenOrder(order);

        assertThrows(ForbiddenException.class, () -> paymentService.createPayment(1L, 7L));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createPayment_whenOrderMissing_throwsNotFound() {
        when(orderRepository.findById(7L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> paymentService.createPayment(1L, 7L));
    }

    @Test
    void createPayment_whenTotalIsZero_refusesBeforeReachingRazorpay() {
        // Razorpay rejects an order below one rupee, so an empty order must not even be sent.
        Order order = unpaidOrder(1L, null, null);
        givenOrder(order);
        givenTotal("0.00");

        assertThrows(InvalidRequestException.class, () -> paymentService.createPayment(1L, 7L));
        verify(razorpay, never()).createOrder(anyLong(), anyString(), anyString());
    }

    @Test
    void verifyPayment_whenSignatureMatches_settlesTheOrderAndConfirmsByMail() {
        Order order = unpaidOrder(1L, "order_RZP1", PaymentStatus.CREATED);
        givenOrder(order);
        givenTotal("1299.00");
        // Signed over the intent stored here, not over anything the client sent.
        when(razorpay.paymentSignatureMatches("order_RZP1|pay_ABC", "deadbeef")).thenReturn(true);
        when(razorpay.currency()).thenReturn("INR");
        givenSettleWins(order);

        GetPaymentStatusResponseDto result = paymentService.verifyPayment(
                1L, 7L, new VerifyPaymentRequestDto("pay_ABC", "deadbeef"));

        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        assertEquals("pay_ABC", order.getPaymentRef());
        assertNotNull(order.getPaidAt());
        assertEquals(PaymentStatus.PAID, result.getPaymentStatus());
        assertEquals(new BigDecimal("1299.00"), result.getTotalPrice());
        verify(notifications).sendOrderConfirmation(1L, 7L, "shopper@example.com");
    }

    @Test
    void verifyPayment_whenSignatureMismatch_throwsAndLeavesTheOrderPayable() {
        Order order = unpaidOrder(1L, "order_RZP1", PaymentStatus.CREATED);
        givenOrder(order);
        when(razorpay.paymentSignatureMatches("order_RZP1|pay_ABC", "bogus")).thenReturn(false);

        assertThrows(InvalidRequestException.class, () -> paymentService.verifyPayment(
                1L, 7L, new VerifyPaymentRequestDto("pay_ABC", "bogus")));

        assertEquals(PaymentStatus.CREATED, order.getPaymentStatus());
        assertNull(order.getPaymentRef());
        verify(orderRepository, never()).settleIfUnpaid(any(), any(), any(), any(), any(), any());
        verify(notifications, never()).sendOrderConfirmation(any(), any(), any());
    }

    @Test
    void verifyPayment_whenNoIntentWasIssued_isRejected() {
        Order order = unpaidOrder(1L, null, null);
        givenOrder(order);

        assertThrows(InvalidRequestException.class, () -> paymentService.verifyPayment(
                1L, 7L, new VerifyPaymentRequestDto("pay_ABC", "deadbeef")));
    }

    @Test
    void verifyPayment_whenAlreadyPaid_replaysStatusWithoutASecondConfirmation() {
        Order order = unpaidOrder(1L, "order_RZP1", PaymentStatus.PAID);
        order.setPaymentRef("pay_ABC");
        givenOrder(order);
        givenTotal("1299.00");
        when(razorpay.currency()).thenReturn("INR");

        GetPaymentStatusResponseDto result = paymentService.verifyPayment(
                1L, 7L, new VerifyPaymentRequestDto("pay_ABC", "deadbeef"));

        assertEquals(PaymentStatus.PAID, result.getPaymentStatus());
        assertEquals("pay_ABC", result.getRazorpayPaymentId());
        verify(orderRepository, never()).save(any());
        verify(notifications, never()).sendOrderConfirmation(any(), any(), any());
    }

    @Test
    void webhook_capturesTheOrderNamedByTheGatewayReference() {
        Order order = unpaidOrder(1L, "order_RZP1", PaymentStatus.CREATED);
        when(razorpay.webhookSignatureMatches(WEBHOOK_BODY, "sig")).thenReturn(true);
        when(razorpay.parseWebhook(WEBHOOK_BODY))
                .thenReturn(new RazorpayClient.WebhookEvent("payment.captured", "order_RZP1", "pay_ABC"));
        when(orderRepository.findByPaymentIntentId("order_RZP1")).thenReturn(Optional.of(order));
        givenOrder(order);
        givenSettleWins(order);

        paymentService.handleWebhook(WEBHOOK_BODY, "sig");

        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        assertEquals("pay_ABC", order.getPaymentRef());
        verify(notifications).sendOrderConfirmation(1L, 7L, "shopper@example.com");
    }

    @Test
    void webhook_whenSignatureDoesNotMatch_isRejectedWithoutReadingThePayload() {
        when(razorpay.webhookSignatureMatches(WEBHOOK_BODY, "forged")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> paymentService.handleWebhook(WEBHOOK_BODY, "forged"));
        verify(orderRepository, never()).findByPaymentIntentId(any());
    }

    @Test
    void webhook_forAnUnknownIntent_isIgnoredSoRazorpayStopsRetrying() {
        when(razorpay.webhookSignatureMatches(WEBHOOK_BODY, "sig")).thenReturn(true);
        when(razorpay.parseWebhook(WEBHOOK_BODY))
                .thenReturn(new RazorpayClient.WebhookEvent("payment.captured", "order_OTHER", "pay_X"));
        when(orderRepository.findByPaymentIntentId("order_OTHER")).thenReturn(Optional.empty());

        paymentService.handleWebhook(WEBHOOK_BODY, "sig");

        verify(orderRepository, never()).settleIfUnpaid(any(), any(), any(), any(), any(), any());
    }

    @Test
    void webhook_paymentFailed_marksTheOrderFailedWithoutSettlingIt() {
        Order order = unpaidOrder(1L, "order_RZP1", PaymentStatus.CREATED);
        when(razorpay.webhookSignatureMatches(WEBHOOK_BODY, "sig")).thenReturn(true);
        when(razorpay.parseWebhook(WEBHOOK_BODY))
                .thenReturn(new RazorpayClient.WebhookEvent("payment.failed", "order_RZP1", "pay_ABC"));
        when(orderRepository.findByPaymentIntentId("order_RZP1")).thenReturn(Optional.of(order));

        paymentService.handleWebhook(WEBHOOK_BODY, "sig");

        // The failure moves the status and nothing else, so a reference from a good capture
        // cannot be blanked by a later failure for a different instrument.
        verify(orderRepository).markFailedIfNotPaid(7L, PaymentStatus.FAILED, PaymentStatus.PAID);
        verify(orderRepository, never()).settleIfUnpaid(any(), any(), any(), any(), any(), any());
        verify(notifications, never()).sendOrderConfirmation(any(), any(), any());
    }

    @Test
    void webhook_repeatingACaptureOnAPaidOrder_doesNotConfirmItTwice() {
        Order order = unpaidOrder(1L, "order_RZP1", PaymentStatus.PAID);
        when(razorpay.webhookSignatureMatches(WEBHOOK_BODY, "sig")).thenReturn(true);
        when(razorpay.parseWebhook(WEBHOOK_BODY))
                .thenReturn(new RazorpayClient.WebhookEvent("order.paid", "order_RZP1", "pay_ABC"));
        when(orderRepository.findByPaymentIntentId("order_RZP1")).thenReturn(Optional.of(order));

        paymentService.handleWebhook(WEBHOOK_BODY, "sig");

        verify(orderRepository, never()).settleIfUnpaid(any(), any(), any(), any(), any(), any());
        verify(notifications, never()).sendOrderConfirmation(any(), any(), any());
    }

    @Test
    void webhook_ignoresEventsWithoutAnOrderReference() {
        when(razorpay.webhookSignatureMatches(WEBHOOK_BODY, "sig")).thenReturn(true);
        when(razorpay.parseWebhook(WEBHOOK_BODY))
                .thenReturn(new RazorpayClient.WebhookEvent("configuration.requested", null, null));

        paymentService.handleWebhook(WEBHOOK_BODY, "sig");

        verify(orderRepository, never()).findByPaymentIntentId(any());
    }

    @Test
    void paymentStatus_isOwnerScopedAndCarriesTheGatewayReferences() {
        Order order = unpaidOrder(1L, "order_RZP1", PaymentStatus.PAID);
        order.setPaymentRef("pay_ABC");
        givenOrder(order);
        givenTotal("1299.00");
        when(razorpay.currency()).thenReturn("INR");

        GetPaymentStatusResponseDto result = paymentService.paymentStatus(1L, 7L);

        assertEquals(7L, result.getOrderId());
        assertEquals("order_RZP1", result.getRazorpayOrderId());
        assertEquals("pay_ABC", result.getRazorpayPaymentId());
        assertEquals(OrderStatus.PENDING, result.getOrderStatus());
        assertEquals("INR", result.getCurrency());
    }

    @Test
    void paymentStatus_forAnotherUsersOrder_isRejected() {
        givenOrder(unpaidOrder(99L, "order_RZP1", PaymentStatus.PAID));

        assertThrows(ForbiddenException.class, () -> paymentService.paymentStatus(1L, 7L));
    }

    @Test
    void createPayment_convertsRupeesToPaiseAtTheOneRupeeBoundary() {
        Order order = unpaidOrder(1L, null, null);
        givenOrder(order);
        // Razorpay's floor is 100 paise: 99 paise is a legitimate price but not a payable
        // order, and sending it would fail at the gateway for a reason the shopper cannot act on.
        givenTotal("0.99");

        assertThrows(InvalidRequestException.class, () -> paymentService.createPayment(1L, 7L));

        givenTotal("1.00");
        when(razorpay.createOrder(100L, "order-7", "7")).thenReturn("order_RZP2");
        when(razorpay.keyId()).thenReturn("rzp_test_123");
        when(razorpay.currency()).thenReturn("INR");

        CreatePaymentResponseDto result = paymentService.createPayment(1L, 7L);

        assertEquals(100L, result.getAmount());
        assertEquals(new BigDecimal("1.00"), result.getTotalPrice());
    }

    @Test
    void createPayment_reusesTheIntentWhileTheOrderStillPricesTheSame() {
        // The storefront calls this again whenever the shopper reopens the pay sheet. A second
        // intent would leave the first one payable at Razorpay but unresolvable here, so the
        // money would land on an order we can no longer match.
        Order order = unpaidOrder(1L, "order_RZP1", PaymentStatus.CREATED);
        order.setPaymentAmountPaise(129900L);
        givenOrder(order);
        givenTotal("1299.00");
        when(razorpay.keyId()).thenReturn("rzp_test_123");
        when(razorpay.currency()).thenReturn("INR");

        CreatePaymentResponseDto result = paymentService.createPayment(1L, 7L);

        assertEquals("order_RZP1", result.getRazorpayOrderId());
        verify(razorpay, never()).createOrder(anyLong(), anyString(), anyString());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createPayment_whenTheOrderWasRepriced_issuesAFreshIntent() {
        Order order = unpaidOrder(1L, "order_RZP1", PaymentStatus.CREATED);
        order.setPaymentAmountPaise(129900L);
        givenOrder(order);
        givenTotal("1499.00");
        when(razorpay.createOrder(149900L, "order-7", "7")).thenReturn("order_RZP9");
        when(razorpay.keyId()).thenReturn("rzp_test_123");
        when(razorpay.currency()).thenReturn("INR");

        CreatePaymentResponseDto result = paymentService.createPayment(1L, 7L);

        assertEquals("order_RZP9", result.getRazorpayOrderId());
        assertEquals(149900L, order.getPaymentAmountPaise().longValue());
    }

    @Test
    void verifyPayment_whenAConcurrentCaptureWon_first_neverMailsTwice() {
        Order order = unpaidOrder(1L, "order_RZP1", PaymentStatus.CREATED);
        givenOrder(order);
        givenTotal("1299.00");
        when(razorpay.paymentSignatureMatches("order_RZP1|pay_ABC", "deadbeef")).thenReturn(true);
        when(razorpay.currency()).thenReturn("INR");
        givenSettleLoses(order);

        GetPaymentStatusResponseDto result = paymentService.verifyPayment(
                1L, 7L, new VerifyPaymentRequestDto("pay_ABC", "deadbeef"));

        assertEquals(PaymentStatus.PAID, result.getPaymentStatus());
        verify(notifications, never()).sendOrderConfirmation(any(), any(), any());
    }

    @Test
    void webhook_paymentFailed_neverReopensAnOrderTheMoneySettled() {
        Order order = unpaidOrder(1L, "order_RZP1", PaymentStatus.PAID);
        when(razorpay.webhookSignatureMatches(WEBHOOK_BODY, "sig")).thenReturn(true);
        when(razorpay.parseWebhook(WEBHOOK_BODY))
                .thenReturn(new RazorpayClient.WebhookEvent("payment.failed", "order_RZP1", "pay_LATE"));
        when(orderRepository.findByPaymentIntentId("order_RZP1")).thenReturn(Optional.of(order));

        paymentService.handleWebhook(WEBHOOK_BODY, "sig");

        // The guard lives in the UPDATE itself: two webhooks for different instruments can
        // arrive together, and the one that reads PAID must not become the one that reopens it.
        verify(orderRepository).markFailedIfNotPaid(7L, PaymentStatus.FAILED, PaymentStatus.PAID);
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
    }
}
