package com.example.inventory.inventory;

import java.util.UUID;

interface LowStockProjection {
    UUID getWarehouseId();
    UUID getProductId();
    String getSku();
    String getName();
    int getQuantity();
    int getReservedQuantity();
    int getAvailableQuantity();
    int getMinimumStock();
}
