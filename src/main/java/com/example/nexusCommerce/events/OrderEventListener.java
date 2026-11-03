package com.example.nexusCommerce.events;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AFTER_COMMIT listeners: run only once the order transaction has durably landed, so
 * downstream effects (analytics, integration fan-out) never see rolled-back state.
 * The @Async marker moves the work off the request thread.
 *
 * <p>Deliberately does not send the confirmation email. An order row exists before any money
 * moves, and the capture path already mails on settlement, so mailing here as well would
 * send every Razorpay shopper two confirmations for one purchase once SMTP is live.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        log.info("Order placed: id={} user={} at={}", event.orderId(), event.userId(), event.placedAt());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        log.info("Order status changed: id={} user={} {} -> {}",
                event.orderId(), event.userId(), event.previous(), event.current());
    }
}
