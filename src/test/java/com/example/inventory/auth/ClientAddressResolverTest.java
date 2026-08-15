package com.example.inventory.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientAddressResolverTest {

    @Test
    void forwardedHeaderIsIgnoredForAnUntrustedPeer() {
        ClientAddressResolver resolver = resolver(List.of("10.0.0.0/8"));
        MockHttpServletRequest request = request("198.51.100.10",
                "203.0.113.25");

        assertEquals("198.51.100.10", resolver.resolve(request));
    }

    @Test
    void trustedProxyChainReturnsTheNearestUntrustedAddress() {
        ClientAddressResolver resolver = resolver(List.of(
                "10.0.0.0/8", "192.0.2.0/24"));
        MockHttpServletRequest request = request("10.0.0.8",
                "198.51.100.30, 203.0.113.40, 192.0.2.15");

        assertEquals("203.0.113.40", resolver.resolve(request));
    }

    @Test
    void invalidTrustedProxyConfigurationFailsAtStartup() {
        assertThrows(IllegalArgumentException.class,
                () -> resolver(List.of("trusted-proxy.example")));
    }

    private ClientAddressResolver resolver(List<String> trustedProxies) {
        return new ClientAddressResolver(new AuthenticationRateLimitProperties(
                5, 100, Duration.ofMinutes(1), 10_000, trustedProxies));
    }

    private MockHttpServletRequest request(String remoteAddress,
                                           String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }
}
