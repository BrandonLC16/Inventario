package com.example.inventory.inventory;

import java.util.UUID;

interface LowStockProjection {

    UUID getProductId();

    String getSku();

    String getName();

    int getQuantity();

    int getMinimumStock();
}
