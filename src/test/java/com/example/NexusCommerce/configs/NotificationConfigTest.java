package com.example.nexusCommerce.configs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import com.example.nexusCommerce.services.LoggingNotificationService;
import com.example.nexusCommerce.services.NotificationService;
import com.example.nexusCommerce.services.SmtpNotificationService;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

/**
 * Which sender is live is decided by properties here rather than by bean-ordering conditions,
 * so the contract worth pinning is the selection itself.
 */
class NotificationConfigTest {

    private final NotificationConfig config = new NotificationConfig();

    @Test
    void withoutTheSwitchOnItStaysTheLoggingSenderSoANewCheckoutNeedsNoCredentials() {
        NotificationService service = config.notificationService(
                senders(mock(JavaMailSender.class)), false, "smtp.resend.com", "mail@nexus.test", "https://shop.test");

        assertInstanceOf(LoggingNotificationService.class, service);
    }

    @Test
    void anEnabledSwitchWithNoHostCannotProduceAnUnsendableMailer() {
        NotificationService service = config.notificationService(
                senders(mock(JavaMailSender.class)), true, "", "mail@nexus.test", "https://shop.test");

        assertInstanceOf(LoggingNotificationService.class, service);
    }

    @Test
    void hostPlusSwitchSelectsSmtpAndNormalisesTheStorefrontUrl() throws Exception {
        MimeMessage message = new MimeMessage((Session) null);
        JavaMailSender mail = mock(JavaMailSender.class);
        when(mail.createMimeMessage()).thenReturn(message);

        NotificationService service = config.notificationService(
                senders(mail), true, "smtp.resend.com", "Nexus Commerce <mail@nexus.test>", "https://shop.test/");

        assertInstanceOf(SmtpNotificationService.class, service);
        service.sendPasswordReset("shopper@example.com", "abc123");

        assertTrue(htmlPart(message).contains("href=\"https://shop.test/auth/reset-password?token=abc123\""));
        assertEquals("Nexus Commerce", ((InternetAddress) message.getFrom()[0]).getPersonal());
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<JavaMailSender> senders(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return provider;
    }

    private static String htmlPart(MimeMessage message) throws Exception {
        return String.valueOf(((MimeMultipart) message.getContent()).getBodyPart(1).getContent());
    }
}
