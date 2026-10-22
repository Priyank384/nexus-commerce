package com.example.nexusCommerce.services;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.nexusCommerce.exceptions.PaymentProviderException;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Razorpay edge: transport, credentials and signature crypto.
 *
 * <p>Separated from {@code RazorpayPaymentServiceImpl} so the key secret is only ever read
 * inside this class (the service asks "does this signature match?" and never sees the secret),
 * and so the order state machine can be unit-tested without a network.
 */
@Component
@Slf4j
public class RazorpayClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    // Built once and shared: HttpClient is thread-safe, and its executor would otherwise be
    // created per call.
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final JsonMapper json = JsonMapper.builder().build();

    @Value("${razorpay.base-url:https://api.razorpay.com/v1}")
    private String baseUrl;

    @Value("${razorpay.key-id:}")
    private String keyId;

    @Value("${razorpay.key-secret:}")
    private String keySecret;

    @Value("${razorpay.webhook-secret:}")
    private String webhookSecret;

    @Value("${razorpay.currency:INR}")
    private String currency;

    public String keyId() {
        return keyId;
    }

    public String currency() {
        return currency;
    }

    /**
     * Creates the gateway-side order for a minor-unit (paise) amount and returns its id.
     * Rejected before the HTTP call when credentials are absent, so a misconfigured deploy
     * fails with an actionable message instead of a 401 from Razorpay.
     */
    public String createOrder(long amountInPaise, String receipt, String notesId) {
        requireCredentials();
        String payload = json.writeValueAsString(Map.of(
                "amount", amountInPaise,
                "currency", currency,
                "receipt", receipt,
                "notes", Map.of("nexus_order_id", notesId)));

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/orders"))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", basicAuth())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        String responseBody = send(request);
        JsonNode node = json.readTree(responseBody);
        String razorpayOrderId = node.path("id").asString(null);
        if (razorpayOrderId == null || razorpayOrderId.isBlank()) {
            throw new PaymentProviderException("Razorpay accepted the order request but returned no order id");
        }
        return razorpayOrderId;
    }

    /**
     * Constant-time comparison of the HMAC Razorpay computes over
     * {@code razorpayOrderId|razorpayPaymentId}. The secret stays here, so the caller can ask
     * the question without holding the credential.
     */
    public boolean paymentSignatureMatches(String signedPayload, String providedSignature) {
        requireCredentials();
        return MessageDigest.isEqual(
                hmac(keySecret, signedPayload).getBytes(StandardCharsets.UTF_8),
                normalizeHex(providedSignature).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Same check for a webhook, over the exact bytes Razorpay signed. Reading the body as a
     * String and re-encoding it would change anything non-ASCII and break the signature, so
     * the raw array is what gets hashed.
     */
    public boolean webhookSignatureMatches(byte[] rawBody, String providedSignature) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new PaymentProviderException(
                    "Razorpay webhook secret is not configured (razorpay.webhook-secret)");
        }
        if (providedSignature == null) {
            return false;
        }
        byte[] expected = hmac(webhookSecret, new String(rawBody, StandardCharsets.UTF_8))
                .getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, normalizeHex(providedSignature).getBytes(StandardCharsets.UTF_8));
    }

    private String send(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new PaymentProviderException("Razorpay could not be reached: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PaymentProviderException("Interrupted while calling Razorpay", ex);
        }
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            // The body of an error response is Razorpay's own description, useful in the log
            // but never echoed to the shopper.
            log.warn("Razorpay rejected {} with HTTP {}: {}", request.uri().getPath(), status, response.body());
            throw new PaymentProviderException("Razorpay rejected the payment request (HTTP " + status + ")");
        }
        return response.body();
    }

    private void requireCredentials() {
        if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
            throw new PaymentProviderException(
                    "Razorpay is not configured on this server (razorpay.key-id / razorpay.key-secret)");
        }
    }

    private String basicAuth() {
        String credentials = keyId + ":" + keySecret;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The two references a webhook carries, pulled out so the rest of the app never has to
     * know Razorpay's payload shape.
     */
    public record WebhookEvent(String event, String razorpayOrderId, String razorpayPaymentId) {
    }

    public WebhookEvent parseWebhook(byte[] rawBody) {
        JsonNode root = json.readTree(rawBody);
        JsonNode orderEntity = root.path("payload").path("order").path("entity");
        JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
        return new WebhookEvent(root.path("event").asString(null),
                orderEntity.path("id").asString(null),
                paymentEntity.path("id").asString(null));
    }

    private static String normalizeHex(String signature) {
        return signature == null ? "" : signature.trim().toLowerCase();
    }

    private static String hmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) {
            // HmacSHA256 is required of every JDK, so this is unreachable in practice.
            throw new PaymentProviderException("Could not compute the payment signature", ex);
        }
    }
}
