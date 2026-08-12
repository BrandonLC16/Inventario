package com.example.inventory.auth;

import com.example.inventory.security.SecurityProperties;
import com.example.inventory.users.RoleName;
import com.example.inventory.users.UserAccount;
import com.example.inventory.users.UserTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-02T03:04:05Z");
    private static final Duration TTL = Duration.ofDays(7);

    private RefreshTokenRepository repository;
    private RefreshTokenService service;
    private UserAccount user;

    @BeforeEach
    void setUp() {
        repository = mock(RefreshTokenRepository.class);
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Jwt("issuer", "audience", Duration.ofMinutes(5), "", ""),
                TTL, new SecurityProperties.Cors(List.of()), false,
                new SecurityProperties.BootstrapAdmin(false, "", "", ""));
        service = new RefreshTokenService(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        user = UserTestFixtures.user("sales", "sales@example.com", "{noop}hidden",
                true, false, RoleName.SALES);
        when(repository.saveAndFlush(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void issueStoresOnlySha256HashWithExpiration() {
        RefreshTokenService.IssuedRefreshToken issued = service.issue(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).saveAndFlush(captor.capture());
        RefreshToken stored = captor.getValue();
        assertEquals(NOW.plus(TTL), stored.getExpiresAt());
        assertEquals(32, stored.getTokenHash().length);
        assertArrayEquals(RefreshTokenService.hash(issued.value()), stored.getTokenHash());
        assertFalse(java.util.Arrays.equals(issued.value().getBytes(StandardCharsets.UTF_8),
                stored.getTokenHash()));
        assertEquals(43, issued.value().length());
    }

    @Test
    void validTokenRotatesAndLinksReplacementInSameFamily() {
        UUID familyId = UUID.randomUUID();
        RefreshToken current = new RefreshToken(user, familyId,
                RefreshTokenService.hash("old-refresh"), NOW.plusSeconds(600));
        RefreshToken replacement = new RefreshToken(user, familyId, new byte[32], NOW.plus(TTL));
        when(repository.findByTokenHashForUpdate(any(byte[].class)))
                .thenReturn(Optional.of(current), Optional.of(replacement));

        RefreshTokenService.RotatedRefreshToken rotated = service.rotate("old-refresh");

        assertSame(user, rotated.user());
        assertNotEquals("old-refresh", rotated.token().value());
        assertTrue(current.isRevoked());
        assertEquals(NOW, current.getRevokedAt());
        assertSame(replacement, current.getReplacedBy());
        assertEquals(familyId, current.getFamilyId());
    }

    @Test
    void expiredTokenIsRevokedAndRejected() {
        RefreshToken expired = new RefreshToken(user, UUID.randomUUID(),
                RefreshTokenService.hash("expired"), NOW);
        when(repository.findByTokenHashForUpdate(any(byte[].class))).thenReturn(Optional.of(expired));

        assertThrows(InvalidAuthenticationException.class, () -> service.rotate("expired"));

        assertTrue(expired.isRevoked());
        assertEquals(NOW, expired.getRevokedAt());
        assertNull(expired.getReplacedBy());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void reuseOfRevokedTokenRevokesItsWholeFamily() {
        UUID familyId = UUID.randomUUID();
        RefreshToken revoked = new RefreshToken(user, familyId,
                RefreshTokenService.hash("used"), NOW.plusSeconds(600));
        revoked.revoke(NOW.minusSeconds(30), null);
        when(repository.findByTokenHashForUpdate(any(byte[].class))).thenReturn(Optional.of(revoked));

        assertThrows(InvalidAuthenticationException.class, () -> service.rotate("used"));

        verify(repository).revokeActiveFamily(familyId, NOW);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void disabledUserRevokesFamilyAndCannotRefresh() {
        UserAccount disabled = UserTestFixtures.user("disabled", "disabled@example.com",
                "{noop}hidden", false, false, RoleName.SALES);
        UUID familyId = UUID.randomUUID();
        RefreshToken token = new RefreshToken(disabled, familyId,
                RefreshTokenService.hash("disabled-token"), NOW.plusSeconds(600));
        when(repository.findByTokenHashForUpdate(any(byte[].class))).thenReturn(Optional.of(token));

        assertThrows(InvalidAuthenticationException.class, () -> service.rotate("disabled-token"));

        verify(repository).revokeActiveFamily(familyId, NOW);
    }

    @Test
    void revokedOrUnknownLogoutIsIdempotentAndNeverStoresPresentedToken() {
        UUID familyId = UUID.randomUUID();
        RefreshToken token = new RefreshToken(user, familyId,
                RefreshTokenService.hash("logout-token"), NOW.plusSeconds(600));
        when(repository.findByTokenHashForUpdate(any(byte[].class)))
                .thenReturn(Optional.of(token), Optional.empty());
        when(repository.revokeActiveFamily(familyId, NOW)).thenReturn(1);

        service.logout("logout-token");
        service.logout("unknown-token");

        verify(repository).revokeActiveFamily(familyId, NOW);
        assertEquals(1L, user.getAccessTokenVersion());
        ArgumentCaptor<byte[]> hash = ArgumentCaptor.forClass(byte[].class);
        verify(repository, org.mockito.Mockito.times(2)).findByTokenHashForUpdate(hash.capture());
        assertArrayEquals(RefreshTokenService.hash("logout-token"), hash.getAllValues().get(0));
        assertArrayEquals(RefreshTokenService.hash("unknown-token"), hash.getAllValues().get(1));
    }
}
