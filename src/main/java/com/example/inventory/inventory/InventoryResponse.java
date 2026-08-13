package com.example.inventory.inventory;

import java.time.Instant;
import java.util.UUID;

public record InventoryResponse(
        UUID productId,
        int quantity,
        int reservedQuantity,
        int availableQuantity,
        Instant updatedAt) {

    static InventoryResponse from(InventoryItem item, int reservedQuantity) {
        return new InventoryResponse(
                item.getProductId(), item.getQuantity(), reservedQuantity,
                item.getQuantity() - reservedQuantity, item.getUpdatedAt());
    }

    static InventoryResponse from(InventoryBalanceProjection balance) {
        return new InventoryResponse(
                balance.getProductId(), balance.getQuantity(),
                balance.getReservedQuantity(),
                balance.getQuantity() - balance.getReservedQuantity(),
                balance.getUpdatedAt());
    }

    static InventoryResponse empty(UUID productId) {
        return new InventoryResponse(productId, 0, 0, 0, null);
    }
}
