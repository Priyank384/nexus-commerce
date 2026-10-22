package com.example.nexusCommerce.exceptions;

/**
 * Raised when the caller is authenticated but the row in question is not theirs.
 * Kept distinct from InvalidRequestException so a failed ownership check answers
 * 403 like the security filter chain's access-denied handler does, instead of
 * telling the storefront the request body was malformed.
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
