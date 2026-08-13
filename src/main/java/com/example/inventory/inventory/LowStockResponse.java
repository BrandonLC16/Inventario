package com.example.inventory.inventory;

import java.util.UUID;

public record LowStockResponse(
        UUID warehouseId,
        UUID productId,
        String sku,
        String name,
        int quantity,
        int reservedQuantity,
        int availableQuantity,
        int minimumStock,
        int replenishmentQuantity,
        StockAlertLevel alert) {

    static LowStockResponse from(LowStockProjection row) {
        return new LowStockResponse(row.getWarehouseId(), row.getProductId(),
                row.getSku(), row.getName(), row.getQuantity(),
                row.getReservedQuantity(), row.getAvailableQuantity(),
                row.getMinimumStock(),
                Math.max(0, row.getMinimumStock() - row.getAvailableQuantity()),
                row.getAvailableQuantity() == 0
                        ? StockAlertLevel.OUT_OF_STOCK
                        : StockAlertLevel.LOW_STOCK);
    }
}
