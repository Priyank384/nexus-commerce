package com.example.nexusCommerce.services;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Default no-op NotificationService: writes the message to the application log so the
 * wiring is visible end-to-end. Provide a JavaMailSender-backed bean to override.
 */
@Slf4j
@Service
@ConditionalOnMissingBean(name = "smtpNotificationService")
public class LoggingNotificationService implements NotificationService {

    @Override
    public void sendOrderConfirmation(Long userId, Long orderId, String email) {
        log.info("[notification-stub] order confirmation to user={} email={} orderId={}", userId, email, orderId);
    }
}
