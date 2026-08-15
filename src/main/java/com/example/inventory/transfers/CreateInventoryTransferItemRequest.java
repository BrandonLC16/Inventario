package com.example.inventory.transfers;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CreateInventoryTransferItemRequest(
        @NotNull UUID productId,
        @Positive int quantity) {
}
