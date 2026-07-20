package com.example.inventory.inventory;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record StockAdjustmentRequest(
        @Schema(description = "Positive to receive stock, negative to consume it", example = "10")
        @NotNull Integer quantityDelta) {
}
