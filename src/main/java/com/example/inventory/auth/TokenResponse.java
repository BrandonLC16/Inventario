package com.example.inventory.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Bearer token pair for clients that can protect credentials. "
        + "The browser MVP must keep both tokens in memory only.")
public record TokenResponse(
        String tokenType,
        @Schema(description = "Short-lived bearer credential; browser clients keep it in memory only")
        String accessToken,
        Instant accessTokenExpiresAt,
        @Schema(description = "Sensitive rotating credential; never persist it in browser storage")
        String refreshToken,
        Instant refreshTokenExpiresAt) {
}
