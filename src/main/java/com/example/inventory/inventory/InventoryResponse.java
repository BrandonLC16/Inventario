package com.example.inventory.inventory;

import java.time.Instant;
import java.util.UUID;

public record InventoryResponse(UUID productId, int quantity, Instant updatedAt) {

    static InventoryResponse from(InventoryItem item) {
        return new InventoryResponse(item.getProductId(), item.getQuantity(), item.getUpdatedAt());
    }

    static InventoryResponse empty(UUID productId) {
        return new InventoryResponse(productId, 0, null);
    }
}
