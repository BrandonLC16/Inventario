package com.example.inventory.inventory;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StockAdjustmentRequest(
        @Schema(description = "Positive to receive stock, negative to consume it", example = "10")
        @NotNull Integer quantityDelta,
        @Size(max = 128) String reference) {

    public StockAdjustmentRequest(Integer quantityDelta) {
        this(quantityDelta, null);
    }
}
