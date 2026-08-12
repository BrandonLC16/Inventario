package com.example.inventory.auth;

import com.example.inventory.security.SecurityProperties;
import com.example.inventory.security.SessionRevoker;
import com.example.inventory.users.UserAccount;
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
    private final SecurityProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository,
                               SecurityProperties properties,
                               Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public IssuedRefreshToken issue(UserAccount user) {
        Instant now = clock.instant();
        return create(user, UUID.randomUUID(), now);
    }

    @Transactional(noRollbackFor = InvalidAuthenticationException.class)
    public RotatedRefreshToken rotate(String presentedToken) {
        Instant now = clock.instant();
        RefreshToken current = repository.findByTokenHashForUpdate(hash(presentedToken))
                .orElseThrow(InvalidAuthenticationException::new);

        if (current.isRevoked()) {
            revokeFamilyAndAccessTokens(current, now);
            throw new InvalidAuthenticationException();
        }
        if (current.isExpiredAt(now)) {
            current.revoke(now, null);
            throw new InvalidAuthenticationException();
        }
        UserAccount user = current.getUser();
        if (!user.isEnabled() || user.isLocked()) {
            revokeFamilyAndAccessTokens(current, now);
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
        repository.findByTokenHashForUpdate(hash(presentedToken))
                .ifPresent(token -> revokeFamilyAndAccessTokens(token, clock.instant()));
    }

    @Override
    @Transactional
    public void revokeAll(UserAccount user) {
        repository.revokeAllActiveByUserId(user.getId(), clock.instant());
        user.revokeAccessTokens();
    }

    private void revokeFamilyAndAccessTokens(RefreshToken token, Instant revokedAt) {
        if (repository.revokeActiveFamily(token.getFamilyId(), revokedAt) > 0) {
            token.getUser().revokeAccessTokens();
        }
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
