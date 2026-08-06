package com.pms.contact;

/** Thrown by ContactRateLimiter when an IP exceeds app.contact.rate-limit.* - maps to 429, see GlobalExceptionHandler. */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
