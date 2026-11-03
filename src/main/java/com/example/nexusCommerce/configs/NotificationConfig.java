package com.example.nexusCommerce.configs;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import com.example.nexusCommerce.services.LoggingNotificationService;
import com.example.nexusCommerce.services.NotificationService;
import com.example.nexusCommerce.services.SmtpNotificationService;

import lombok.extern.slf4j.Slf4j;

/**
 * Chooses the notification sender in one place instead of leaning on bean-ordering
 * conditions, so which implementation is live is a fact this method can state. SMTP needs
 * both the switch and a host; anything less falls back to logging, which keeps a fresh
 * checkout and the test suite bootable with no credentials.
 */
@Slf4j
@Configuration
public class NotificationConfig {

    @Bean
    NotificationService notificationService(
            ObjectProvider<JavaMailSender> senders,
            @Value("${app.mail.enabled:false}") boolean enabled,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${app.mail.from:noreply@nexuscommerce.test}") String from,
            @Value("${app.mail.storefront-base-url:http://localhost:3000}") String storefrontBaseUrl) {
        JavaMailSender sender = enabled && !mailHost.isBlank() ? senders.getIfAvailable() : null;
        if (sender == null) {
            if (enabled) {
                log.warn("app.mail.enabled=true but no spring.mail.host/MAIL_HOST is configured; "
                        + "emails will only be logged");
            }
            return new LoggingNotificationService();
        }
        log.info("Transactional email enabled from {} to {}", from, mailHost);
        return new SmtpNotificationService(sender, from, storefrontBaseUrl.replaceAll("/+$", ""));
    }
}
