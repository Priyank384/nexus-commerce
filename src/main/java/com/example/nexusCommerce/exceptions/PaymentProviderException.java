package com.example.nexusCommerce.exceptions;

/**
 * The payment gateway could not do what was asked: it is not configured on this server, it
 * rejected the request, or it was unreachable.
 *
 * <p>Its own type because a gateway failure is neither the caller's fault (400) nor a bug in
 * this service (500) -- and a shopper whose bank attempt failed must be able to tell that
 * apart from a broken checkout button.
 */
public class PaymentProviderException extends RuntimeException {

    public PaymentProviderException(String message) {
        super(message);
    }

    public PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
