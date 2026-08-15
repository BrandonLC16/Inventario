package com.example.inventory.counts;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record InventoryCountResponse(
        UUID id,
        String folio,
        UUID warehouseId,
        InventoryCountStatus status,
        InventoryCountScope scope,
        List<InventoryCountLineResponse> lines,
        Instant openedAt,
        String openedBy,
        Instant submittedAt,
        String submittedBy,
        Instant postedAt,
        String postedBy,
        Instant cancelledAt,
        String cancelledBy) {

    public InventoryCountResponse {
        lines = List.copyOf(lines);
    }

    static InventoryCountResponse from(InventoryCount inventoryCount) {
        List<InventoryCountLineResponse> lineResponses = inventoryCount
                .getLines().stream()
                .sorted(Comparator.comparing(InventoryCountLine::getProductId))
                .map(InventoryCountLineResponse::from)
                .toList();
        return new InventoryCountResponse(
                inventoryCount.getId(), inventoryCount.getFolio(),
                inventoryCount.getWarehouseId(), inventoryCount.getStatus(),
                inventoryCount.getScope(), lineResponses,
                inventoryCount.getOpenedAt(), inventoryCount.getOpenedBy(),
                inventoryCount.getSubmittedAt(),
                inventoryCount.getSubmittedBy(), inventoryCount.getPostedAt(),
                inventoryCount.getPostedBy(), inventoryCount.getCancelledAt(),
                inventoryCount.getCancelledBy());
    }
}
