package com.example.inventory.transfers;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record InventoryTransferResponse(
        UUID id,
        String folio,
        UUID sourceWarehouseId,
        UUID destinationWarehouseId,
        InventoryTransferStatus status,
        List<InventoryTransferItemResponse> items,
        long totalQuantity,
        long inTransitQuantity,
        Instant createdAt,
        String createdBy,
        Instant dispatchedAt,
        String dispatchedBy,
        Instant receivedAt,
        String receivedBy,
        Instant cancelledAt,
        String cancelledBy) {

    public InventoryTransferResponse {
        items = List.copyOf(items);
    }

    static InventoryTransferResponse from(InventoryTransfer transfer) {
        List<InventoryTransferItemResponse> items = transfer.getItems().stream()
                .sorted(Comparator.comparing(InventoryTransferItem::getProductId))
                .map(item -> InventoryTransferItemResponse.from(
                        item, transfer.getStatus()))
                .toList();
        long totalQuantity = items.stream()
                .mapToLong(InventoryTransferItemResponse::quantity).sum();
        long inTransitQuantity = items.stream()
                .mapToLong(InventoryTransferItemResponse::inTransitQuantity).sum();
        return new InventoryTransferResponse(
                transfer.getId(), transfer.getFolio(),
                transfer.getSourceWarehouseId(),
                transfer.getDestinationWarehouseId(), transfer.getStatus(),
                items, totalQuantity, inTransitQuantity,
                transfer.getCreatedAt(), transfer.getCreatedBy(),
                transfer.getDispatchedAt(), transfer.getDispatchedBy(),
                transfer.getReceivedAt(), transfer.getReceivedBy(),
                transfer.getCancelledAt(), transfer.getCancelledBy());
    }
}
