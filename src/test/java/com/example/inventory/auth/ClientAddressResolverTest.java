package com.example.inventory.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @ParameterizedTest
    @ValueSource(strings = {
            "bad.cafe",
            "localhost",
            "client.example",
            "fe80::1%eth0",
            "[fe80::1%25eth0]",
            "127.1",
            "0177.0.0.1",
            "192.0.2.10:443",
            "[192.0.2.10]",
            "192.0.2.10."
    })
    void nonLiteralOrAmbiguousForwardedAddressIsRejected(String forwardedFor) {
        ClientAddressResolver resolver = resolver(List.of("10.0.0.0/8"));
        MockHttpServletRequest request = request("10.0.0.8", forwardedFor);

        assertEquals("10.0.0.8", resolver.resolve(request));
    }

    @Test
    void ipv6LiteralFromATrustedProxyIsAccepted() {
        ClientAddressResolver resolver = resolver(List.of("10.0.0.0/8"));
        MockHttpServletRequest request = request("10.0.0.8", "2001:db8::25");

        assertEquals("2001:db8:0:0:0:0:0:25", resolver.resolve(request));
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
