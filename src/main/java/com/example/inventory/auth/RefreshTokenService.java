package com.example.inventory.auth;

import com.example.inventory.security.SecurityProperties;
import com.example.inventory.security.SessionRevoker;
import com.example.inventory.users.UserAccount;
import com.example.inventory.users.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class RefreshTokenService implements SessionRevoker {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final UserAccountRepository users;
    private final SecurityProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository,
                               UserAccountRepository users,
                               SecurityProperties properties,
                               Clock clock) {
        this.repository = repository;
        this.users = users;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public IssuedRefreshToken issue(UserAccount user) {
        UserAccount lockedUser = lockUser(user.getId());
        if (!lockedUser.isEnabled() || lockedUser.isLocked()) {
            throw new InvalidAuthenticationException();
        }
        Instant now = clock.instant();
        return create(lockedUser, UUID.randomUUID(), now);
    }

    @Transactional(noRollbackFor = InvalidAuthenticationException.class)
    public RotatedRefreshToken rotate(String presentedToken) {
        Instant now = clock.instant();
        byte[] tokenHash = hash(presentedToken);
        UUID userId = repository.findUserIdByTokenHash(tokenHash)
                .orElseThrow(InvalidAuthenticationException::new);
        UserAccount user = lockUserWithRoles(userId);
        RefreshToken current = repository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(InvalidAuthenticationException::new);

        if (current.isRevoked()) {
            revokeFamilyAndAccessTokens(current, user, now);
            throw new InvalidAuthenticationException();
        }
        if (current.isExpiredAt(now)) {
            current.revoke(now, null);
            throw new InvalidAuthenticationException();
        }
        if (!user.isEnabled() || user.isLocked()) {
            revokeFamilyAndAccessTokens(current, user, now);
            throw new InvalidAuthenticationException();
        }

        IssuedRefreshToken issued = create(user, current.getFamilyId(), now);
        RefreshToken replacement = repository.findByTokenHashForUpdate(hash(issued.value()))
                .orElseThrow(IllegalStateException::new);
        current.revoke(now, replacement);
        return new RotatedRefreshToken(user, issued);
    }

    @Transactional
    public void logout(String presentedToken) {
        byte[] tokenHash = hash(presentedToken);
        repository.findUserIdByTokenHash(tokenHash).ifPresent(userId -> {
            UserAccount user = lockUser(userId);
            repository.findByTokenHashForUpdate(tokenHash)
                    .ifPresent(token -> revokeFamilyAndAccessTokens(
                            token, user, clock.instant()));
        });
    }

    @Override
    @Transactional
    public void revokeAll(UserAccount user) {
        UserAccount lockedUser = lockUser(user.getId());
        repository.revokeAllActiveByUserId(lockedUser.getId(), clock.instant());
        lockedUser.revokeAccessTokens();
    }

    private void revokeFamilyAndAccessTokens(RefreshToken token, UserAccount user,
                                             Instant revokedAt) {
        if (repository.revokeActiveFamily(token.getFamilyId(), revokedAt) > 0) {
            user.revokeAccessTokens();
        }
    }

    private UserAccount lockUser(UUID userId) {
        return users.findByIdForUpdate(userId)
                .orElseThrow(InvalidAuthenticationException::new);
    }

    private UserAccount lockUserWithRoles(UUID userId) {
        lockUser(userId);
        return users.findByIdWithRoles(userId)
                .orElseThrow(InvalidAuthenticationException::new);
    }

    private IssuedRefreshToken create(UserAccount user, UUID familyId, Instant now) {
        String value = newTokenValue();
        Instant expiresAt = now.plus(properties.refreshTokenTtl());
        repository.saveAndFlush(new RefreshToken(user, familyId, hash(value), expiresAt));
        return new IssuedRefreshToken(value, expiresAt);
    }

    private String newTokenValue() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static byte[] hash(String token) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record IssuedRefreshToken(String value, Instant expiresAt) {
    }

    public record RotatedRefreshToken(UserAccount user, IssuedRefreshToken token) {
    }
}
