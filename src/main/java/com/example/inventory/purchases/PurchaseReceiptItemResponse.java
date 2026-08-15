package com.example.inventory.purchases;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseReceiptItemResponse(
        UUID purchaseOrderItemId,
        int quantity,
        BigDecimal unitCost) {

    static PurchaseReceiptItemResponse from(PurchaseReceiptItem item) {
        return new PurchaseReceiptItemResponse(
                item.getPurchaseOrderItemId(), item.getQuantity(), item.getUnitCost());
    }
}
