package com.example.nexusCommerce.exceptions;

/**
 * A soft control (per-IP or per-account failure counter) refused the request. Mapped to 429
 * so a client can back off instead of treating it as bad credentials.
 */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
