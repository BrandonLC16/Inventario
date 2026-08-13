package com.example.inventory.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties("inventory.security")
public record SecurityProperties(
        Jwt jwt,
        Duration refreshTokenTtl,
        Cors cors,
        boolean swaggerEnabled,
        BootstrapAdmin bootstrapAdmin) {

    private static final Duration MIN_ACCESS_TTL = Duration.ofMinutes(1);
    private static final Duration MAX_ACCESS_TTL = Duration.ofHours(1);
    private static final Duration MIN_REFRESH_TTL = Duration.ofHours(1);
    private static final Duration MAX_REFRESH_TTL = Duration.ofDays(90);

    public SecurityProperties {
        requireDuration(refreshTokenTtl, MIN_REFRESH_TTL, MAX_REFRESH_TTL, "refresh token TTL");
        if (cors != null && cors.allowedOrigins() != null
                && cors.allowedOrigins().stream().map(String::trim).anyMatch("*"::equals)) {
            throw new IllegalArgumentException("CORS wildcard origins are not allowed");
        }
    }

    public record Jwt(
            String issuer,
            String audience,
            Duration accessTokenTtl,
            String publicKeyLocation,
            String privateKeyLocation) {

        public Jwt {
            issuer = requireText(issuer, "JWT issuer");
            audience = requireText(audience, "JWT audience");
            requireDuration(accessTokenTtl, MIN_ACCESS_TTL, MAX_ACCESS_TTL, "access token TTL");
        }
    }
    public record Cors(List<String> allowedOrigins) {
        public Cors {
            allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        }
    }

    public record BootstrapAdmin(
            boolean enabled,
            String username,
            String email,
            String password) {
    }

    private static String requireText(String value, String description) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(description + " must not be blank");
        }
        return value.trim();
    }

    private static void requireDuration(Duration value, Duration minimum, Duration maximum,
                                        String description) {
        if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(description + " must be between "
                    + minimum + " and " + maximum);
        }
    }
}
