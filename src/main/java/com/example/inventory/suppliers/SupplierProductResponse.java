package com.example.inventory.suppliers;

import java.math.BigDecimal;
import java.util.UUID;

public record SupplierProductResponse(
        UUID supplierId,
        UUID productId,
        String supplierSku,
        int leadTimeDays,
        int minimumOrderQuantity,
        BigDecimal lastUnitCost,
        boolean preferred,
        boolean active) {

    static SupplierProductResponse from(SupplierProduct supplierProduct) {
        return new SupplierProductResponse(
                supplierProduct.getSupplierId(), supplierProduct.getProductId(),
                supplierProduct.getSupplierSku(), supplierProduct.getLeadTimeDays(),
                supplierProduct.getMinimumOrderQuantity(),
                supplierProduct.getLastUnitCost(), supplierProduct.isPreferred(),
                supplierProduct.isActive());
    }
}
