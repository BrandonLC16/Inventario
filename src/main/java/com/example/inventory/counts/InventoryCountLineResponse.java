package com.example.inventory.counts;

import java.time.Instant;
import java.util.UUID;

public record InventoryCountLineResponse(
        UUID id,
        UUID productId,
        Integer expectedQuantity,
        Integer countedQuantity,
        Integer variance,
        Instant countedAt,
        String countedBy,
        String notes) {

    static InventoryCountLineResponse from(InventoryCountLine line) {
        return new InventoryCountLineResponse(
                line.getId(), line.getProductId(), line.getExpectedQuantity(),
                line.getCountedQuantity(), line.getVariance(),
                line.getCountedAt(), line.getCountedBy(), line.getNotes());
    }
}
