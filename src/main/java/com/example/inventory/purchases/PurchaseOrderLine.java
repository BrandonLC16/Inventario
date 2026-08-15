package com.example.inventory.purchases;

import java.math.BigDecimal;
import java.util.UUID;

record PurchaseOrderLine(
        UUID productId,
        String supplierSku,
        int orderedQuantity,
        BigDecimal unitCost) {
}
