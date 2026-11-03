package com.example.nexusCommerce.services;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Real SMTP sender, chosen by {@link com.example.nexusCommerce.configs.NotificationConfig}
 * when app.mail.enabled=true.
 *
 * <p>Sending is best-effort by design: a transport failure is logged and swallowed. The
 * callers are an after-commit listener and the payment-capture path, both of which have
 * already durably recorded the money, and letting an unreachable SMTP turn that into a
 * failed request would make Razorpay retry a webhook whose settle guard now reports
 * "already settled" and never mails at all.
 */
@Slf4j
@RequiredArgsConstructor
public class SmtpNotificationService implements NotificationService {

    /** The store ships INR-priced goods to India, so receipts read in one clock. Fixed for testability. */
    private static final ZoneId STORE_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE = DateTimeFormatter
            .ofPattern("d MMM yyyy, HH:mm 'IST'", Locale.ROOT);

    private final JavaMailSender mail;
    private final String from;
    private final String storefrontBaseUrl;

    @Override
    public void sendOrderConfirmation(OrderReceipt receipt) {
        String orderUrl = storefrontBaseUrl + "/orders/" + receipt.orderId();
        // Only a guest order has one: it is the single-order secret, not the session credential,
        // precisely because this URL leaves the browser and sits in an inbox.
        String claimUrl = receipt.claimToken() == null ? null
                : storefrontBaseUrl + "/orders/claim?token=" + receipt.claimToken();
        send(receipt.toEmail(),
                "Nexus Commerce order #" + receipt.orderId() + " confirmed",
                orderText(receipt, orderUrl, claimUrl),
                orderHtml(receipt, orderUrl, claimUrl));
    }

    @Override
    public void sendPasswordReset(String toEmail, String resetToken) {
        String resetUrl = storefrontBaseUrl + "/auth/reset-password?token=" + resetToken;
        send(toEmail, "Reset your Nexus Commerce password",
                "Use this link to set a new password. It expires in 15 minutes and works once: " + resetUrl
                        + "\n\nIf you did not request this, ignore the email and your password stays as it is.",
                """
                <div style="font-family:-apple-system,Segoe UI,Roboto,sans-serif;max-width:520px;margin:0 auto;color:#111">
                  <p style="font-size:19px;margin:0 0 16px">Password reset</p>
                  <p style="margin:0 0 16px">We got a request to reset the password on your Nexus Commerce account.</p>
                  <p style="margin:0 0 20px">
                    <a href="%s" style="background:#111;color:#fff;text-decoration:none;padding:12px 20px;border-radius:8px;display:inline-block">Choose a new password</a>
                  </p>
                  <p style="margin:0 0 8px;color:#555">This link expires in 15 minutes and can only be used once.</p>
                  <p style="margin:0;color:#555">Did not ask for this? Ignore the email &mdash; your password stays unchanged.</p>
                </div>
                """.formatted(esc(resetUrl)));
    }

    private String orderHtml(OrderReceipt receipt, String orderUrl, String claimUrl) {
        StringBuilder rows = new StringBuilder();
        for (OrderReceipt.Line line : receipt.lines()) {
            rows.append("""
                    <tr>
                      <td style="padding:10px 0;border-bottom:1px solid #eee">%s%s</td>
                      <td style="padding:10px 0;border-bottom:1px solid #eee;text-align:center">%d</td>
                    </tr>
                    """.formatted(esc(line.name()),
                    line.variant() == null || line.variant().isBlank() ? "" : " &middot; " + esc(line.variant()),
                    line.quantity()));
        }
        return """
                <div style="font-family:-apple-system,Segoe UI,Roboto,sans-serif;max-width:560px;margin:0 auto;color:#111">
                  <p style="font-size:19px;margin:0 0 4px">Thanks for your order!</p>
                  <p style="margin:0 0 20px;color:#555">Order #%d &middot; %s</p>
                  <table style="width:100%%;border-collapse:collapse;font-size:15px">
                    <thead>
                      <tr style="text-align:left;color:#777;font-size:13px">
                        <th style="padding-bottom:6px;font-weight:600">Item</th>
                        <th style="padding-bottom:6px;font-weight:600;text-align:center">Qty</th>
                      </tr>
                    </thead>
                    <tbody>%s</tbody>
                  </table>
                  <p style="margin:20px 0 4px;font-size:17px"><strong>Total %s</strong></p>
                  <p style="margin:0 0 24px;color:#555">Payment captured &mdash; we will email you again when it ships.</p>
                  <p style="margin:0">
                    <a href="%s" style="color:#111">Track this order</a>
                  </p>%s
                </div>
                """.formatted(receipt.orderId(),
                esc(receipt.placedAt() == null ? "" : DATE.format(receipt.placedAt().atZone(STORE_ZONE))),
                rows, money(receipt.total()), esc(orderUrl),
                claimUrl == null ? "" : """

                        <p style="margin:16px 0 0;color:#555">Bought this as a guest?
                          <a href="%s" style="color:#111">Save it to an account</a> to keep it in your order history.
                        </p>
                        """.formatted(esc(claimUrl)));
    }

    private String orderText(OrderReceipt receipt, String orderUrl, String claimUrl) {
        StringBuilder text = new StringBuilder("Thanks for your order!\n\n")
                .append("Order #").append(receipt.orderId());
        if (receipt.placedAt() != null) {
            text.append("  ").append(DATE.format(receipt.placedAt().atZone(STORE_ZONE)));
        }
        text.append("\n\n");
        for (OrderReceipt.Line line : receipt.lines()) {
            text.append(" - ").append(line.name());
            if (line.variant() != null && !line.variant().isBlank()) {
                text.append(" (").append(line.variant()).append(")");
            }
            text.append(" x").append(line.quantity()).append('\n');
        }
        return text.append("\nTotal ").append(money(receipt.total()))
                .append("\n\nTrack this order: ").append(orderUrl).append('\n')
                .append("Payment captured. We will email you again when it ships.\n")
                .append(claimUrl == null ? ""
                        : "\nBought this as a guest? Save it to an account so it stays in your "
                                + "order history: " + claimUrl + "\n")
                .toString();
    }

    private void send(String to, String subject, String text, String html) {
        try {
            MimeMessage message = mail.createMimeMessage();
            message.setFrom(new InternetAddress(from));
            message.setRecipients(MimeMessage.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject, StandardCharsets.UTF_8.name());
            MimeMultipart alternative = new MimeMultipart("alternative");
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setContent(text, "text/plain; charset=UTF-8");
            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(html, "text/html; charset=UTF-8");
            alternative.addBodyPart(textPart);
            alternative.addBodyPart(htmlPart);
            message.setContent(alternative);
            mail.send(message);
            log.info("Sent '{}' to {}", subject, to);
        } catch (MailException | MessagingException ex) {
            log.error("Could not send '{}' to {}: {}", subject, to, ex.getMessage());
        }
    }

    private static String money(BigDecimal amount) {
        return "₹" + String.format(Locale.ROOT, "%,.2f", amount == null ? BigDecimal.ZERO : amount);
    }

    /** Product names and addresses are operator/shopper input and land inside HTML here. */
    private static String esc(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
