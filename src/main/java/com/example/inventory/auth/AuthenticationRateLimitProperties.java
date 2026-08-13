package com.example.inventory.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("inventory.security.authentication-rate-limit")
public record AuthenticationRateLimitProperties(
        int maxAttemptsPerCredential,
        int maxAttemptsPerClient,
        Duration window,
        int maxTrackedKeys) {

    private static final Duration MIN_WINDOW = Duration.ofSeconds(1);
    private static final Duration MAX_WINDOW = Duration.ofHours(1);

    public AuthenticationRateLimitProperties {
        requireRange(maxAttemptsPerCredential, 1, 1000,
                "maximum attempts per credential");
        requireRange(maxAttemptsPerClient, maxAttemptsPerCredential, 100_000,
                "maximum attempts per client");
        if (window == null || window.compareTo(MIN_WINDOW) < 0
                || window.compareTo(MAX_WINDOW) > 0) {
            throw new IllegalArgumentException(
                    "Authentication rate-limit window must be between 1 second and 1 hour");
        }
        requireRange(maxTrackedKeys, 100, 1_000_000,
                "maximum tracked authentication keys");
    }

    private static void requireRange(int value, int minimum, int maximum,
                                     String description) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(description + " must be between "
                    + minimum + " and " + maximum);
        }
    }
}
