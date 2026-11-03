package com.example.nexusCommerce.services;

import lombok.extern.slf4j.Slf4j;

/**
 * Fallback {@link NotificationService}: writes the message to the application log so the
 * wiring is visible end-to-end on a checkout with no SMTP credentials. Selected by
 * {@link com.example.nexusCommerce.configs.NotificationConfig}.
 */
@Slf4j
public class LoggingNotificationService implements NotificationService {

    @Override
    public void sendOrderConfirmation(OrderReceipt receipt) {
        log.info("[notification-stub] order confirmation to email={} orderId={} total={} lines={} claimLink={}",
                receipt.toEmail(), receipt.orderId(), receipt.total(), receipt.lines().size(),
                receipt.claimToken() != null);
    }

    @Override
    public void sendPasswordReset(String toEmail, String resetToken) {
        log.info("[notification-stub] password reset to email={} token={}", toEmail, resetToken);
    }
}
