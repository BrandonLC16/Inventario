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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-02T03:04:05Z");

    private AuthenticationManager authenticationManager;
    private RefreshTokenService refreshTokens;
    private JwtService jwtService;
    private UserAccountRepository users;
    private PasswordEncoder passwordEncoder;
    private AuthService service;

    @BeforeEach
    void setUp() {
        authenticationManager = mock(AuthenticationManager.class);
        refreshTokens = mock(RefreshTokenService.class);
        jwtService = mock(JwtService.class);
        users = mock(UserAccountRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new AuthService(authenticationManager, refreshTokens, jwtService, users,
                passwordEncoder, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void validCredentialsIssueAccessAndRefreshTokensWithoutPasswordHash() {
        UserAccount user = UserTestFixtures.user("admin", "admin@example.com", "{noop}hidden",
                true, false, RoleName.ADMIN);
        InventoryUserDetails principal = InventoryUserDetails.from(user);
        Authentication authenticated = mock(Authentication.class);
        when(authenticated.getPrincipal()).thenReturn(principal);
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authenticated);
        when(users.findByIdentifierForUpdate("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct password", user.getPasswordHash())).thenReturn(true);
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
        var order = inOrder(users, authenticationManager, refreshTokens);
        order.verify(users).findByIdentifierForUpdate("admin");
        order.verify(authenticationManager).authenticate(any(Authentication.class));
        order.verify(refreshTokens).issue(user);
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
    void unknownIdentifierUsesGenericFailureThroughThePasswordVerifier() {
        when(users.findByIdentifierForUpdate("gone")).thenReturn(Optional.empty());
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("invalid"));

        InvalidAuthenticationException exception = assertThrows(InvalidAuthenticationException.class,
                () -> service.login(new LoginRequest("gone", "correct password")));

        assertEquals("Authentication failed", exception.getMessage());
        verify(authenticationManager).authenticate(any());
        verify(refreshTokens, never()).issue(any());
    }

    @Test
    void passwordChangedAfterAuthenticationCannotIssueANewSession() {
        UserAccount user = UserTestFixtures.user("racing", "racing@example.com", "old-hash",
                true, false, RoleName.SALES);
        Authentication authenticated = mock(Authentication.class);
        when(authenticated.getPrincipal()).thenReturn(InventoryUserDetails.from(user));
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authenticated);
        user.replacePasswordHash("new-hash");
        when(users.findByIdentifierForUpdate("racing")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-password", "new-hash")).thenReturn(false);

        assertThrows(InvalidAuthenticationException.class,
                () -> service.login(new LoginRequest("racing", "old-password")));

        verify(refreshTokens, never()).issue(any());
        verify(jwtService, never()).issue(any(), any());
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
