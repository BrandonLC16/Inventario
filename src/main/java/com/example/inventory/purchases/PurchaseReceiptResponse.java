package com.example.inventory.purchases;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record PurchaseReceiptResponse(
        UUID id,
        String folio,
        UUID purchaseOrderId,
        UUID warehouseId,
        String externalReference,
        Boolean updateSupplierProductLastCost,
        List<PurchaseReceiptItemResponse> items,
        Instant receivedAt,
        String receivedBy) {

    public PurchaseReceiptResponse {
        items = List.copyOf(items);
    }

    static PurchaseReceiptResponse from(PurchaseReceipt receipt,
                                        List<PurchaseReceiptItem> receiptItems) {
        var items = receiptItems.stream()
                .sorted(Comparator.comparing(PurchaseReceiptItem::getPurchaseOrderItemId))
                .map(PurchaseReceiptItemResponse::from)
                .toList();
        return new PurchaseReceiptResponse(
                receipt.getId(), receipt.getFolio(), receipt.getPurchaseOrderId(),
                receipt.getWarehouseId(), receipt.getExternalReference(),
                receipt.getUpdateSupplierProductLastCost(), items,
                receipt.getReceivedAt(), receipt.getReceivedBy());
    }
}
