package com.example.inventory.transfers;

import java.util.UUID;

public record InventoryTransferItemResponse(
        UUID id,
        UUID productId,
        int quantity,
        int inTransitQuantity) {

    static InventoryTransferItemResponse from(
            InventoryTransferItem item, InventoryTransferStatus status) {
        return new InventoryTransferItemResponse(
                item.getId(), item.getProductId(), item.getQuantity(),
                status == InventoryTransferStatus.IN_TRANSIT
                        ? item.getQuantity() : 0);
    }
}
