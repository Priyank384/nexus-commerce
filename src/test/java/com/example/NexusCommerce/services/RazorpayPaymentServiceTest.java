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

        assertThrows(InvalidRequestException.class, () -> paymentService.createPayment(1L, 7L));
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
        verify(orderRepository, never()).save(any());
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

        paymentService.handleWebhook(WEBHOOK_BODY, "sig");

        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        assertEquals("pay_ABC", order.getPaymentRef());
        verify(orderRepository).save(order);
        verify(notifications).sendOrderConfirmation(1L, 7L, "shopper@example.com");
    }

    @Test
    void webhook_whenSignatureDoesNotMatch_isRejectedWithoutReadingThePayload() {
        when(razorpay.webhookSignatureMatches(WEBHOOK_BODY, "forged")).thenReturn(false);

        assertThrows(InvalidRequestException.class, () -> paymentService.handleWebhook(WEBHOOK_BODY, "forged"));
        verify(orderRepository, never()).findByPaymentIntentId(any());
    }

    @Test
    void webhook_forAnUnknownIntent_isIgnoredSoRazorpayStopsRetrying() {
        when(razorpay.webhookSignatureMatches(WEBHOOK_BODY, "sig")).thenReturn(true);
        when(razorpay.parseWebhook(WEBHOOK_BODY))
                .thenReturn(new RazorpayClient.WebhookEvent("payment.captured", "order_OTHER", "pay_X"));
        when(orderRepository.findByPaymentIntentId("order_OTHER")).thenReturn(Optional.empty());

        paymentService.handleWebhook(WEBHOOK_BODY, "sig");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void webhook_paymentFailed_marksTheOrderFailedWithoutSettlingIt() {
        Order order = unpaidOrder(1L, "order_RZP1", PaymentStatus.CREATED);
        when(razorpay.webhookSignatureMatches(WEBHOOK_BODY, "sig")).thenReturn(true);
        when(razorpay.parseWebhook(WEBHOOK_BODY))
                .thenReturn(new RazorpayClient.WebhookEvent("payment.failed", "order_RZP1", "pay_ABC"));
        when(orderRepository.findByPaymentIntentId("order_RZP1")).thenReturn(Optional.of(order));

        paymentService.handleWebhook(WEBHOOK_BODY, "sig");

        assertEquals(PaymentStatus.FAILED, order.getPaymentStatus());
        assertNull(order.getPaymentRef());
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

        verify(orderRepository, never()).save(any());
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

        assertThrows(InvalidRequestException.class, () -> paymentService.paymentStatus(1L, 7L));
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
}
