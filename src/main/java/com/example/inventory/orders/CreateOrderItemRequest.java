package com.example.inventory.orders;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CreateOrderItemRequest(
        @NotNull UUID productId,
        @Positive @Max(MAX_QUANTITY) int quantity) {

    public static final int MAX_QUANTITY = 1_000_000;
}
