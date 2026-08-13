package com.example.inventory.inventory;

import java.time.Instant;
import java.util.UUID;

interface InventoryBalanceProjection {
    UUID getWarehouseId();
    UUID getProductId();
    int getQuantity();
    int getReservedQuantity();
    Instant getUpdatedAt();
}
