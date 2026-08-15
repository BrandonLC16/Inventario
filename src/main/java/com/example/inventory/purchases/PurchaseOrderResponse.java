package com.example.inventory.purchases;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderResponse(
        UUID id,
        String folio,
        UUID supplierId,
        UUID destinationWarehouseId,
        PurchaseOrderStatus status,
        String currency,
        BigDecimal total,
        String supplierReference,
        List<PurchaseOrderItemResponse> items,
        Instant issuedAt,
        String issuedBy,
        Instant cancelledAt,
        String cancelledBy,
        Instant createdAt,
        String createdBy,
        Instant updatedAt) {

    public PurchaseOrderResponse {
        items = List.copyOf(items);
    }

    static PurchaseOrderResponse from(PurchaseOrder order) {
        List<PurchaseOrderItemResponse> items = order.getItems().stream()
                .sorted(Comparator.comparing(PurchaseOrderItem::getProductId))
                .map(PurchaseOrderItemResponse::from)
                .toList();
        return new PurchaseOrderResponse(
                order.getId(), order.getFolio(), order.getSupplierId(),
                order.getDestinationWarehouseId(), order.getStatus(),
                order.getCurrency(), order.getTotal(), order.getSupplierReference(),
                items, order.getIssuedAt(), order.getIssuedBy(),
                order.getCancelledAt(), order.getCancelledBy(), order.getCreatedAt(),
                order.getCreatedBy(), order.getUpdatedAt());
    }
}
