package com.example.inventory.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
final class ClientAddressResolver {
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final int MAX_HEADER_LENGTH = 1024;
    private static final int MAX_FORWARDED_ADDRESSES = 16;

    private final List<Network> trustedProxies;

    ClientAddressResolver(AuthenticationRateLimitProperties properties) {
        trustedProxies = properties.trustedProxies().stream()
                .map(Network::parse)
                .toList();
    }

    String resolve(HttpServletRequest request) {
        Optional<InetAddress> peer = parseAddress(request.getRemoteAddr());
        String fallback = peer.map(ClientAddressResolver::format)
                .orElse("unknown");
        if (peer.isEmpty() || trustedProxies.isEmpty()
                || !isTrusted(peer.get())) {
            return fallback;
        }

        List<InetAddress> forwarded = forwardedAddresses(request);
        if (forwarded.isEmpty()) return fallback;
        for (int index = forwarded.size() - 1; index >= 0; index--) {
            InetAddress candidate = forwarded.get(index);
            if (!isTrusted(candidate)) return format(candidate);
        }
        return format(forwarded.get(0));
    }

    private List<InetAddress> forwardedAddresses(HttpServletRequest request) {
        List<InetAddress> result = new ArrayList<>();
        var headers = request.getHeaders(FORWARDED_FOR_HEADER);
        while (headers.hasMoreElements()) {
            String header = headers.nextElement();
            if (header.length() > MAX_HEADER_LENGTH) return List.of();
            for (String value : header.split(",", -1)) {
                if (result.size() >= MAX_FORWARDED_ADDRESSES) return List.of();
                Optional<InetAddress> address = parseAddress(value);
                if (address.isEmpty()) return List.of();
                result.add(address.get());
            }
        }
        return List.copyOf(result);
    }

    private boolean isTrusted(InetAddress address) {
        return trustedProxies.stream().anyMatch(network -> network.contains(address));
    }

    private static Optional<InetAddress> parseAddress(String rawValue) {
        if (rawValue == null) return Optional.empty();
        String value = rawValue.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.isEmpty() || !value.matches("[0-9A-Fa-f:.]+")) {
            return Optional.empty();
        }
        try {
            return Optional.of(InetAddress.getByName(value));
        } catch (UnknownHostException exception) {
            return Optional.empty();
        }
    }

    private static String format(InetAddress address) {
        return address.getHostAddress().toLowerCase(Locale.ROOT);
    }

    private static final class Network {
        private final byte[] address;
        private final int prefixLength;

        private Network(byte[] address, int prefixLength) {
            this.address = address.clone();
            this.prefixLength = prefixLength;
        }

        static Network parse(String value) {
            String[] parts = value.split("/", -1);
            if (parts.length > 2) {
                throw invalid(value);
            }
            InetAddress address = parseAddress(parts[0])
                    .orElseThrow(() -> invalid(value));
            int maximumPrefix = address.getAddress().length * Byte.SIZE;
            int prefix = maximumPrefix;
            if (parts.length == 2) {
                try {
                    prefix = Integer.parseInt(parts[1]);
                } catch (NumberFormatException exception) {
                    throw invalid(value);
                }
            }
            if (prefix < 0 || prefix > maximumPrefix) throw invalid(value);
            return new Network(address.getAddress(), prefix);
        }

        boolean contains(InetAddress candidate) {
            byte[] candidateAddress = candidate.getAddress();
            if (candidateAddress.length != address.length) return false;
            int completeBytes = prefixLength / Byte.SIZE;
            for (int index = 0; index < completeBytes; index++) {
                if (candidateAddress[index] != address[index]) return false;
            }
            int remainingBits = prefixLength % Byte.SIZE;
            if (remainingBits == 0) return true;
            int mask = 0xff << (Byte.SIZE - remainingBits);
            return (candidateAddress[completeBytes] & mask)
                    == (address[completeBytes] & mask);
        }

        private static IllegalArgumentException invalid(String value) {
            return new IllegalArgumentException(
                    "Invalid trusted proxy address or CIDR: " + value);
        }
    }
}
