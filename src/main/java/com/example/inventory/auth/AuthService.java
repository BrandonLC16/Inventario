package com.example.inventory.auth;

import com.example.inventory.security.InventoryUserDetails;
import com.example.inventory.security.JwtService;
import com.example.inventory.users.UserAccount;
import com.example.inventory.users.UserAccountRepository;
import com.example.inventory.shared.BadRequestException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokens;
    private final JwtService jwtService;
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AuthService(AuthenticationManager authenticationManager,
                       RefreshTokenService refreshTokens,
                       JwtService jwtService,
                       UserAccountRepository users,
                       PasswordEncoder passwordEncoder,
                       Clock clock) {
        this.authenticationManager = authenticationManager;
        this.refreshTokens = refreshTokens;
        this.jwtService = jwtService;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public TokenResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.identifier().trim(), request.password()));
            if (authentication == null
                    || !(authentication.getPrincipal() instanceof InventoryUserDetails principal)) {
                throw new InvalidAuthenticationException();
            }
            UserAccount user = users.findByIdWithRoles(principal.id())
                    .orElseThrow(InvalidAuthenticationException::new);
            return issuePair(InventoryUserDetails.from(user), refreshTokens.issue(user));
        } catch (AuthenticationException exception) {
            throw new InvalidAuthenticationException();
        }
    }

    public TokenResponse refresh(RefreshRequest request) {
        RefreshTokenService.RotatedRefreshToken rotated = refreshTokens.rotate(request.refreshToken());
        return issuePair(InventoryUserDetails.from(rotated.user()), rotated.token());
    }

    public void logout(RefreshRequest request) {
        refreshTokens.logout(request.refreshToken());
    }

    @Transactional
    public void changePassword(String subject, ChangePasswordRequest request) {
        UserAccount user = requireActiveUser(subject);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is invalid");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("New password must be different from the current password");
        }
        user.replacePasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokens.revokeAll(user);
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse me(String subject) {
        UserAccount user = requireActiveUser(subject);
        return new CurrentUserResponse(user.getId(), user.getUsername(), user.getEmail(),
                user.getRoles().stream().map(role -> role.getName())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    private UserAccount requireActiveUser(String subject) {
        UUID userId;
        try {
            userId = UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            throw new InvalidAuthenticationException();
        }
        return users.findByIdWithRoles(userId)
                .filter(UserAccount::isEnabled)
                .filter(account -> !account.isLocked())
                .orElseThrow(InvalidAuthenticationException::new);
    }

    private TokenResponse issuePair(InventoryUserDetails user,
                                    RefreshTokenService.IssuedRefreshToken refreshToken) {
        Instant now = clock.instant();
        JwtService.IssuedAccessToken accessToken = jwtService.issue(user, now);
        return new TokenResponse("Bearer", accessToken.value(), accessToken.expiresAt(),
                refreshToken.value(), refreshToken.expiresAt());
    }
}
