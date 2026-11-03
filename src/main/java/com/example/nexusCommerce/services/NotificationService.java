package com.example.nexusCommerce.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbound email seam. {@link NotificationConfig} picks the SMTP sender when
 * app.mail.enabled=true and a logging fallback otherwise, so the rest of the app never
 * has to know whether a message actually left the building.
 */
public interface NotificationService {

    void sendOrderConfirmation(OrderReceipt receipt);

    /**
     * @param resetToken the single-use secret from the store's token service. The link is
     *                   assembled here because this is the component that already knows the
     *                   storefront origin, so no caller has to duplicate that knowledge.
     */
    void sendPasswordReset(String toEmail, String resetToken);

    /**
     * Everything one confirmation needs, handed over by the code that already loaded it.
     * Carrying the line items here is what keeps the sender from re-querying an order
     * inside an after-commit listener, where a lazy collection would fail.
     *
     * @param toEmail the address the order was placed under; a guest order uses the email
     *                captured at checkout, so this is the only recipient the service knows
     * @param total   the sum of the price snapshots written at checkout, not a re-pricing
     * @param claimToken the single-order link a guest was mailed, or null for an order that
     *                   already belongs to an account
     */
    record OrderReceipt(String toEmail, Long orderId, BigDecimal total, LocalDateTime placedAt,
                        List<Line> lines, String claimToken) {
        /** @param variant the size/colour the shopper picked, or null for a single-format product */
        public record Line(String name, int quantity, String variant) {}
    }
}
