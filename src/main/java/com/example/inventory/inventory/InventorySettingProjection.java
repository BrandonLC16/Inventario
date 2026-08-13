package com.example.inventory.inventory;

import java.util.UUID;

interface InventorySettingProjection {
    UUID getWarehouseId();
    UUID getProductId();
    String getSku();
    String getName();
    int getMinimumStock();
    boolean getActive();
}
