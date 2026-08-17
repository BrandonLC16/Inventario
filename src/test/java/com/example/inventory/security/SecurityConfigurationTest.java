package com.example.inventory.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SecurityConfigurationTest {

    @Test
    void directBrowserApiDoesNotAllowCredentialedCorsRequests() {
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Jwt("inventory-test", "inventory-clients",
                        Duration.ofMinutes(15), "unused-public-key", "unused-private-key"),
                Duration.ofDays(14),
                new SecurityProperties.Cors(List.of("https://inventory.example")),
                false,
                new SecurityProperties.BootstrapAdmin(false, "", "", ""));
        CorsConfigurationSource source =
                new SecurityConfiguration().corsConfigurationSource(properties);

        CorsConfiguration configuration = source.getCorsConfiguration(
                new MockHttpServletRequest("GET", "/api/v1/products"));

        assertNotNull(configuration);
        assertEquals(List.of("https://inventory.example"),
                configuration.getAllowedOrigins());
        assertFalse(Boolean.TRUE.equals(configuration.getAllowCredentials()));
    }
}
