package com.example.inventory.auth;

final class AuthenticationRateLimitException extends RuntimeException {
    private final long retryAfterSeconds;

    AuthenticationRateLimitException(long retryAfterSeconds) {
        super("Too many authentication attempts");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
