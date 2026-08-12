package com.example.inventory.security;

import com.example.inventory.users.RoleName;
import com.example.inventory.users.UserAccount;
import com.example.inventory.users.UserTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import java.time.Clock;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtSecurityTest {

    private static final Instant NOW = Instant.parse("2030-01-02T03:04:05Z");

    private RsaTestSupport keys;
    private SecurityProperties properties;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        keys = RsaTestSupport.create();
        properties = properties(keys);
        decoder = new SecurityConfiguration().jwtDecoder(properties, new DefaultResourceLoader(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void accessTokenContainsRequiredClaimsAndExpectedAuthorities() {
        UserAccount account = UserTestFixtures.user("manager", "manager@example.com", "{noop}secret",
                true, false, RoleName.INVENTORY_MANAGER, RoleName.SALES);
        InventoryUserDetails principal = InventoryUserDetails.from(account);

        JwtService.IssuedAccessToken issued =
                new JwtService(keys.encoder(SignatureAlgorithm.RS256), properties).issue(principal, NOW);
        var jwt = decoder.decode(issued.value());

        assertEquals(account.getId().toString(), jwt.getSubject());
        assertEquals("inventory-test", jwt.getClaimAsString("iss"));
        assertEquals(List.of("inventory-tests"), jwt.getAudience());
        assertEquals(NOW, jwt.getIssuedAt());
        assertEquals(NOW.plusSeconds(300), jwt.getExpiresAt());
        assertEquals(36, jwt.getId().length());
        assertEquals(List.of("INVENTORY_MANAGER", "SALES"), jwt.getClaimAsStringList("roles"));
        assertEquals(0L, ((Number) jwt.getClaim(
                AccessTokenValidator.TOKEN_VERSION_CLAIM)).longValue());
        assertEquals(issued.expiresAt(), jwt.getExpiresAt());
        assertTrue(principal.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_INVENTORY_MANAGER")));
        assertTrue(principal.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_SALES")));
    }

    @Test
    void decoderRejectsExpiredToken() {
        Instant expiredAt = Instant.now().minusSeconds(400);
        assertThrows(JwtException.class, () -> decoder.decode(token(keys, properties,
                expiredAt.minusSeconds(300), expiredAt, "inventory-test",
                List.of("inventory-tests"), UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), List.of("ADMIN"), SignatureAlgorithm.RS256)));
    }

    @Test
    void decoderRejectsInvalidSignature() {
        RsaTestSupport attacker = RsaTestSupport.create();
        assertThrows(JwtException.class, () -> decoder.decode(token(attacker, properties,
                Instant.now(), Instant.now().plusSeconds(300), "inventory-test",
                List.of("inventory-tests"), UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), List.of("ADMIN"), SignatureAlgorithm.RS256)));
    }

    @Test
    void decoderRejectsWrongIssuerOrAudience() {
        Instant now = Instant.now();
        assertThrows(JwtException.class, () -> decoder.decode(token(keys, properties,
                now, now.plusSeconds(300), "other-issuer", List.of("inventory-tests"),
                UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                List.of("ADMIN"), SignatureAlgorithm.RS256)));
        assertThrows(JwtException.class, () -> decoder.decode(token(keys, properties,
                now, now.plusSeconds(300), "inventory-test", List.of("other-audience"),
                UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                List.of("ADMIN"), SignatureAlgorithm.RS256)));
    }

    @Test
    void decoderRejectsUnexpectedAlgorithmAndMissingOrMalformedRequiredClaims() {
        Instant now = Instant.now();
        assertThrows(JwtException.class, () -> decoder.decode(token(keys, properties,
                now, now.plusSeconds(300), "inventory-test", List.of("inventory-tests"),
                UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                List.of("ADMIN"), SignatureAlgorithm.RS512)));
        assertThrows(JwtException.class, () -> decoder.decode(token(keys, properties,
                now, now.plusSeconds(300), "inventory-test", List.of("inventory-tests"),
                "not-a-uuid", "not-a-uuid", List.of("SUPERUSER"), SignatureAlgorithm.RS256)));
    }

    @Test
    void decoderRejectsFutureIssuedAtAndExcessiveLifetime() {
        assertThrows(JwtException.class, () -> decoder.decode(token(keys, properties,
                NOW.plusSeconds(61), NOW.plusSeconds(361), "inventory-test",
                List.of("inventory-tests"), UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), List.of("ADMIN"), SignatureAlgorithm.RS256)));
        assertThrows(JwtException.class, () -> decoder.decode(token(keys, properties,
                NOW, NOW.plusSeconds(301), "inventory-test",
                List.of("inventory-tests"), UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), List.of("ADMIN"), SignatureAlgorithm.RS256)));
    }



    static SecurityProperties properties(RsaTestSupport keys) {
        return new SecurityProperties(
                new SecurityProperties.Jwt("inventory-test", "inventory-tests",
                        Duration.ofMinutes(5), keys.publicKeyLocation(), keys.privateKeyLocation()),
                Duration.ofDays(7), new SecurityProperties.Cors(List.of("https://allowed.example")),
                false, new SecurityProperties.BootstrapAdmin(false, "", "", ""));
    }

    private static String token(RsaTestSupport signingKeys, SecurityProperties properties,
                                Instant issuedAt, Instant expiresAt, String issuer,
                                List<String> audience, String subject, String jti,
                                List<String> roles, SignatureAlgorithm algorithm) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer).audience(audience).issuedAt(issuedAt).expiresAt(expiresAt)
                .subject(subject).id(jti).claim("roles", roles)
                .claim(AccessTokenValidator.TOKEN_VERSION_CLAIM, 0L).build();
        JwsHeader header = JwsHeader.with(algorithm).type("JWT").build();
        return signingKeys.encoder(algorithm)
                .encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
