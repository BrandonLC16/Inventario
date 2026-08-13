package com.example.inventory.auth;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
final class AuthenticationRateLimiter {
    private static final int CLEANUP_INTERVAL = 64;

    private final AuthenticationRateLimitProperties properties;
    private final Clock clock;
    private final Map<String, AttemptWindow> attempts =
            new LinkedHashMap<>(128, 0.75f, true);
    private int operations;

    AuthenticationRateLimiter(AuthenticationRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    void checkLogin(String remoteAddress, String identifier) {
        String client = fingerprint(requireValue(remoteAddress));
        String credential = fingerprint(identifier.trim().toLowerCase(Locale.ROOT));
        check("login:client:" + client, properties.maxAttemptsPerClient());
        check(credentialKey("login", client, credential),
                properties.maxAttemptsPerCredential());
    }

    synchronized void loginSucceeded(String remoteAddress, String identifier) {
        attempts.remove(credentialKey("login",
                fingerprint(requireValue(remoteAddress)),
                fingerprint(identifier.trim().toLowerCase(Locale.ROOT))));
    }

    void checkRefresh(String remoteAddress, String refreshToken) {
        String client = fingerprint(requireValue(remoteAddress));
        String credential = fingerprint(refreshToken);
        check("refresh:client:" + client, properties.maxAttemptsPerClient());
        check(credentialKey("refresh", client, credential),
                properties.maxAttemptsPerCredential());
    }

    synchronized void refreshSucceeded(String remoteAddress, String refreshToken) {
        attempts.remove(credentialKey("refresh",
                fingerprint(requireValue(remoteAddress)), fingerprint(refreshToken)));
    }

    private synchronized void check(String key, int limit) {
        Instant now = clock.instant();
        if (++operations % CLEANUP_INTERVAL == 0) removeExpired(now);

        AttemptWindow current = attempts.get(key);
        if (current == null || !now.isBefore(current.startedAt().plus(properties.window()))) {
            ensureCapacity();
            attempts.put(key, new AttemptWindow(now, 1));
            return;
        }
        if (current.count() >= limit) {
            throw new AuthenticationRateLimitException(
                    retryAfterSeconds(now, current.startedAt()));
        }
        attempts.put(key, new AttemptWindow(current.startedAt(), current.count() + 1));
    }

    private void removeExpired(Instant now) {
        attempts.entrySet().removeIf(entry ->
                !now.isBefore(entry.getValue().startedAt().plus(properties.window())));
    }

    private void ensureCapacity() {
        if (attempts.size() < properties.maxTrackedKeys()) return;
        Iterator<String> eldest = attempts.keySet().iterator();
        if (eldest.hasNext()) {
            eldest.next();
            eldest.remove();
        }
    }

    private long retryAfterSeconds(Instant now, Instant startedAt) {
        long millis = Duration.between(now, startedAt.plus(properties.window())).toMillis();
        return Math.max(1, Math.floorDiv(millis + 999, 1000));
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

    private static String credentialKey(String operation, String client,
                                        String credential) {
        return operation + ":credential:" + client + ':' + credential;
    }

    private record AttemptWindow(Instant startedAt, int count) { }
}
