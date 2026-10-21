package com.example.nexusCommerce.events;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.extern.slf4j.Slf4j;

/**
 * AFTER_COMMIT listeners: run only once the order transaction has durably landed, so
 * downstream effects (email, analytics, integration fan-out) never see rolled-back state.
 * The @Async marker moves the work off the request thread.
 */
@Slf4j
@Component
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
