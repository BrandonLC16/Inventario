package com.example.inventory.security;

import com.example.inventory.users.UserAccountRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.OptionalLong;
import java.util.UUID;

@Component
class AccessTokenValidator implements OAuth2TokenValidator<Jwt> {

    static final String TOKEN_VERSION_CLAIM = "token_version";

    private static final OAuth2Error INVALID_TOKEN = new OAuth2Error(
            "invalid_token", "The access token has been revoked", null);

    private final UserAccountRepository users;

    AccessTokenValidator(UserAccountRepository users) {
        this.users = users;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String subject = token.getSubject();
        if (subject == null) return failure();
        UUID userId;
        try {
            userId = UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            return failure();
        }
        OptionalLong version = tokenVersion(token);
        if (version.isEmpty()
                || !users.isAccessTokenActive(userId, version.getAsLong())) {
            return failure();
        }
        return OAuth2TokenValidatorResult.success();
    }

    static OptionalLong tokenVersion(Jwt token) {
        Object claim = token.getClaims().get(TOKEN_VERSION_CLAIM);
        if (!(claim instanceof Number number)) {
            return OptionalLong.empty();
        }
        long value = number.longValue();
        if (value < 0 || number.doubleValue() != (double) value) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(value);
    }

    private OAuth2TokenValidatorResult failure() {
        return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
    }
}
