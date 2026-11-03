package com.example.nexusCommerce.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

class SmtpNotificationServiceTest {

    private final JavaMailSender mail = mock(JavaMailSender.class);
    private final SmtpNotificationService service =
            new SmtpNotificationService(mail, "Nexus Commerce <mail@nexus.test>", "https://shop.nexus.test");

    @Test
    void orderConfirmation_isHtmlPlusTextAndCarriesTheLineItemsAndTotal() throws Exception {
        NotificationService.OrderReceipt receipt = new NotificationService.OrderReceipt(
                "shopper@example.com", 7L, new BigDecimal("12990.00"),
                LocalDateTime.of(2026, 11, 2, 18, 30),
                List.of(new NotificationService.OrderReceipt.Line("Organic Cotton Crew Tee", 2, "M / Sand"),
                        new NotificationService.OrderReceipt.Line("Wool Overcoat", 1, null)),
                null);
        stubMimeMessage();

        service.sendOrderConfirmation(receipt);

        MimeMessage sent = capture();
        assertEquals("Nexus Commerce order #7 confirmed", sent.getSubject());
        assertEquals("mail@nexus.test", ((InternetAddress) sent.getFrom()[0]).getAddress());
        assertEquals("Nexus Commerce", ((InternetAddress) sent.getFrom()[0]).getPersonal());
        assertEquals("shopper@example.com",
                ((InternetAddress) sent.getRecipients(MimeMessage.RecipientType.TO)[0]).getAddress());

        MimeMultipart body = assertInstanceOf(MimeMultipart.class, sent.getContent());
        assertEquals("multipart/alternative", body.getContentType().split(";")[0]);
        String text = String.valueOf(body.getBodyPart(0).getContent());
        String html = String.valueOf(body.getBodyPart(1).getContent());
        assertTrue(text.contains("Organic Cotton Crew Tee (M / Sand) x2"));
        assertTrue(text.contains("Total ₹12,990.00"));
        assertTrue(html.contains("Wool Overcoat"));
        assertTrue(html.contains("https://shop.nexus.test/orders/7"));
        assertFalse(html.contains("/orders/claim"), "an account order has nothing to claim");
    }

    @Test
    void orderConfirmationOffersTheClaimLinkOnlyToAGuest() throws Exception {
        NotificationService.OrderReceipt receipt = new NotificationService.OrderReceipt(
                "shopper@example.com", 9L, new BigDecimal("499.00"), LocalDateTime.now(),
                List.of(new NotificationService.OrderReceipt.Line("Cotton Ankle Socks", 3, "S / White")),
                "kQ7mZ2xT9vB4cL6pR8sY1uW3eA5nJ0hD7gF2kM9qXbE");
        stubMimeMessage();

        service.sendOrderConfirmation(receipt);

        MimeMultipart body = assertInstanceOf(MimeMultipart.class, capture().getContent());
        String link = "https://shop.nexus.test/orders/claim?token=kQ7mZ2xT9vB4cL6pR8sY1uW3eA5nJ0hD7gF2kM9qXbE";
        assertTrue(String.valueOf(body.getBodyPart(0).getContent()).contains(link));
        assertTrue(String.valueOf(body.getBodyPart(1).getContent()).contains(link));
    }

    @Test
    void orderConfirmation_escapesShopperAndOperatorTextSoItCannotBecomeMarkup() throws Exception {
        NotificationService.OrderReceipt receipt = new NotificationService.OrderReceipt(
                "shopper@example.com", 8L, BigDecimal.ONE, LocalDateTime.now(),
                List.of(new NotificationService.OrderReceipt.Line(
                        "<script>alert('xss')</script>", 1, "One & Only")),
                null);
        stubMimeMessage();

        service.sendOrderConfirmation(receipt);

        MimeMultipart body = assertInstanceOf(MimeMultipart.class, capture().getContent());
        String html = String.valueOf(body.getBodyPart(1).getContent());
        assertFalse(html.contains("<script>"), "raw tag would execute in the mail client");
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("One &amp; Only"));
    }

    @Test
    void passwordReset_linksToTheStorefrontWithTheTokenItWasGiven() throws Exception {
        stubMimeMessage();

        service.sendPasswordReset("shopper@example.com", "abc123");

        MimeMessage sent = capture();
        MimeMultipart body = assertInstanceOf(MimeMultipart.class, sent.getContent());
        assertEquals("Reset your Nexus Commerce password", sent.getSubject());
        String link = "https://shop.nexus.test/auth/reset-password?token=abc123";
        assertTrue(String.valueOf(body.getBodyPart(0).getContent()).contains(link));
        assertTrue(String.valueOf(body.getBodyPart(1).getContent()).contains(link));
    }

    @Test
    void anUnreachableMailServer_isLoggedAndSwallowedInsteadOfFailingTheCapture() {
        stubMimeMessage();
        doThrow(new MailSendException("smtp refused")).when(mail).send(any(MimeMessage.class));

        assertDoesNotThrow(() -> service.sendPasswordReset("shopper@example.com", "abc123"));
    }

    private void stubMimeMessage() {
        when(mail.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    }

    private MimeMessage capture() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mail).send(captor.capture());
        return captor.getValue();
    }
}
