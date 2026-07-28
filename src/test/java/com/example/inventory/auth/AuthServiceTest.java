package com.example.inventory.auth;

import com.example.inventory.security.InventoryUserDetails;
import com.example.inventory.security.JwtService;
import com.example.inventory.users.RoleName;
import com.example.inventory.users.UserAccount;
import com.example.inventory.users.UserAccountRepository;
import com.example.inventory.users.UserTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-02T03:04:05Z");

    private AuthenticationManager authenticationManager;
    private RefreshTokenService refreshTokens;
    private JwtService jwtService;
    private UserAccountRepository users;
    private AuthService service;

    @BeforeEach
    void setUp() {
        authenticationManager = mock(AuthenticationManager.class);
        refreshTokens = mock(RefreshTokenService.class);
        jwtService = mock(JwtService.class);
        users = mock(UserAccountRepository.class);
        service = new AuthService(authenticationManager, refreshTokens, jwtService, users,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void validCredentialsIssueAccessAndRefreshTokensWithoutPasswordHash() {
        UserAccount user = UserTestFixtures.user("admin", "admin@example.com", "{noop}hidden",
                true, false, RoleName.ADMIN);
        InventoryUserDetails principal = InventoryUserDetails.from(user);
        Authentication authenticated = mock(Authentication.class);
        when(authenticated.getPrincipal()).thenReturn(principal);
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authenticated);
        when(users.findByIdWithRoles(user.getId())).thenReturn(Optional.of(user));
        when(refreshTokens.issue(user)).thenReturn(
                new RefreshTokenService.IssuedRefreshToken("refresh-value", NOW.plusSeconds(600)));
        when(jwtService.issue(any(InventoryUserDetails.class), any(Instant.class))).thenReturn(
                new JwtService.IssuedAccessToken("access-value", NOW.plusSeconds(300)));

        TokenResponse response = service.login(new LoginRequest(" ADMIN ", "correct password"));

        assertEquals("Bearer", response.tokenType());
        assertEquals("access-value", response.accessToken());
        assertEquals("refresh-value", response.refreshToken());
        verify(authenticationManager).authenticate(any(Authentication.class));
        verify(jwtService).issue(any(InventoryUserDetails.class), any(Instant.class));
    }

    @ParameterizedTest
    @MethodSource("accountStatusFailures")
    void disabledOrLockedCredentialsUseTheSameGenericFailure(RuntimeException failure) {
        when(authenticationManager.authenticate(any(Authentication.class))).thenThrow(failure);

        InvalidAuthenticationException exception = assertThrows(InvalidAuthenticationException.class,
                () -> service.login(new LoginRequest("someone", "correct password")));

        assertEquals("Authentication failed", exception.getMessage());
        verify(refreshTokens, never()).issue(any());
        verify(jwtService, never()).issue(any(), any());
    }

    static Stream<RuntimeException> accountStatusFailures() {
        return Stream.of(new DisabledException("disabled detail"), new LockedException("locked detail"));
    }

    @Test
    void authenticatedPrincipalMissingFromDatabaseUsesGenericFailure() {
        UserAccount user = UserTestFixtures.user("gone", "gone@example.com", "{noop}hidden",
                true, false, RoleName.SALES);
        Authentication authenticated = mock(Authentication.class);
        when(authenticated.getPrincipal()).thenReturn(InventoryUserDetails.from(user));
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authenticated);
        when(users.findByIdWithRoles(user.getId())).thenReturn(Optional.empty());

        InvalidAuthenticationException exception = assertThrows(InvalidAuthenticationException.class,
                () -> service.login(new LoginRequest("gone", "correct password")));

        assertEquals("Authentication failed", exception.getMessage());
        verify(refreshTokens, never()).issue(any());
    }

    @Test
    void refreshIssuesAccessTokenForTheRotatedUser() {
        UserAccount user = UserTestFixtures.user("sales", "sales@example.com", "{noop}hidden",
                true, false, RoleName.SALES);
        var issuedRefresh =
                new RefreshTokenService.IssuedRefreshToken("new-refresh", NOW.plusSeconds(600));
        when(refreshTokens.rotate("old-refresh"))
                .thenReturn(new RefreshTokenService.RotatedRefreshToken(user, issuedRefresh));
        when(jwtService.issue(any(InventoryUserDetails.class), any(Instant.class))).thenReturn(
                new JwtService.IssuedAccessToken("new-access", NOW.plusSeconds(300)));

        TokenResponse response = service.refresh(new RefreshRequest("old-refresh"));

        assertEquals("new-access", response.accessToken());
        assertEquals("new-refresh", response.refreshToken());
    }
}
