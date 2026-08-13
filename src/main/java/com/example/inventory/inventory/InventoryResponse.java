package com.example.inventory.inventory;

import java.time.Instant;
import java.util.UUID;

public record InventoryResponse(
        UUID warehouseId,
        UUID productId,
        int quantity,
        int reservedQuantity,
        int availableQuantity,
        Instant updatedAt) {

    static InventoryResponse from(InventoryItem item, int reservedQuantity) {
        return new InventoryResponse(item.getWarehouseId(), item.getProductId(),
                item.getQuantity(), reservedQuantity,
                item.getQuantity() - reservedQuantity, item.getUpdatedAt());
    }

    static InventoryResponse from(InventoryBalanceProjection balance) {
        return new InventoryResponse(balance.getWarehouseId(), balance.getProductId(),
                balance.getQuantity(), balance.getReservedQuantity(),
                balance.getQuantity() - balance.getReservedQuantity(),
                balance.getUpdatedAt());
    }

    static InventoryResponse empty(UUID warehouseId, UUID productId) {
        return new InventoryResponse(warehouseId, productId, 0, 0, 0, null);
    }
}
