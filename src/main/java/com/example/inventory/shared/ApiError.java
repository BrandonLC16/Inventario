package com.example.inventory.shared;

import java.time.Instant;
import java.util.Map;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        ApiErrorCode code,
        String message,
        String path,
        String correlationId,
        Map<String, String> validationErrors) {
}
