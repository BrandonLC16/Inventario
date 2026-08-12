package com.example.inventory.orders;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CreateOrderItemRequest(
        @NotNull UUID productId,
        @Positive int quantity) {
}
