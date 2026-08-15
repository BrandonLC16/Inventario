package com.example.inventory.purchases;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseOrderItemResponse(
        UUID id,
        UUID productId,
        String supplierSku,
        int orderedQuantity,
        int receivedQuantity,
        int pendingQuantity,
        BigDecimal unitCost,
        BigDecimal subtotal) {

    static PurchaseOrderItemResponse from(PurchaseOrderItem item) {
        return new PurchaseOrderItemResponse(
                item.getId(), item.getProductId(), item.getSupplierSku(),
                item.getOrderedQuantity(), item.getReceivedQuantity(),
                item.pendingQuantity(), item.getUnitCost(), item.getSubtotal());
    }
}
