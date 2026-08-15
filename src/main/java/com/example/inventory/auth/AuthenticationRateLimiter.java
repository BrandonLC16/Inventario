package com.example.inventory.auth;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

@Component
final class AuthenticationRateLimiter {
    private static final long CLEANUP_INTERVAL = 64;

    private final AuthenticationRateLimitProperties properties;
    private final AuthenticationRateLimitRepository repository;
    private final AtomicLong operations = new AtomicLong();

    AuthenticationRateLimiter(AuthenticationRateLimitProperties properties,
                              AuthenticationRateLimitRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    void checkLogin(String clientAddress, String identifier) {
        checkClient("login", clientAddress);
        checkCredential("login",
                identifier.trim().toLowerCase(Locale.ROOT));
    }

    void loginSucceeded(String identifier) {
        repository.delete(credentialKey("login",
                identifier.trim().toLowerCase(Locale.ROOT)));
    }

    void checkRefresh(String clientAddress, String refreshToken) {
        checkClient("refresh", clientAddress);
        checkCredential("refresh", refreshToken);
    }

    void refreshSucceeded(String refreshToken) {
        repository.delete(credentialKey("refresh", refreshToken));
    }

    void checkLogout(String clientAddress, String refreshToken) {
        checkClient("logout", clientAddress);
        checkCredential("logout", refreshToken);
    }

    private void checkClient(String operation, String clientAddress) {
        check(operation + ":client:" + fingerprint(
                        requireValue(clientAddress)),
                properties.maxAttemptsPerClient());
    }

    private void checkCredential(String operation, String credential) {
        check(credentialKey(operation, credential),
                properties.maxAttemptsPerCredential());
    }

    private void check(String key, int limit) {
        if (operations.incrementAndGet() % CLEANUP_INTERVAL == 0) {
            repository.deleteExpired(properties.maxTrackedKeys());
        }
        AuthenticationRateLimitRepository.Attempt attempt = repository
                .increment(key, properties.window(), properties.maxTrackedKeys())
                .orElseThrow(() -> new AuthenticationRateLimitException(
                        Math.max(1, properties.window().toSeconds())));
        if (attempt.count() > limit) {
            throw new AuthenticationRateLimitException(
                    attempt.retryAfterSeconds());
        }
    }

    private static String credentialKey(String operation, String credential) {
        return operation + ":credential:" + fingerprint(credential);
    }

    private static String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String requireValue(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
