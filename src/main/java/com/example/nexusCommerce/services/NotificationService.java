package com.example.nexusCommerce.services;

/**
 * Outbound email seam. The default implementation logs; swap the bean for a
 * JavaMailSender-backed one in production and everything downstream stays put.
 */
public interface NotificationService {
    void sendOrderConfirmation(Long userId, Long orderId, String email);
}
