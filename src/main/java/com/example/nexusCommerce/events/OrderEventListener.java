package com.example.nexusCommerce.events;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.nexusCommerce.repositories.UserRepository;
import com.example.nexusCommerce.services.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AFTER_COMMIT listeners: run only once the order transaction has durably landed, so
 * downstream effects (email, analytics, integration fan-out) never see rolled-back state.
 * The @Async marker moves the work off the request thread.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final NotificationService notifications;
    private final UserRepository userRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        log.info("Order placed: id={} user={} at={}", event.orderId(), event.userId(), event.placedAt());
        userRepository.findById(event.userId())
                .ifPresent(u -> notifications.sendOrderConfirmation(u.getId(), event.orderId(), u.getEmail()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        log.info("Order status changed: id={} user={} {} -> {}",
                event.orderId(), event.userId(), event.previous(), event.current());
    }
}
