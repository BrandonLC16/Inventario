package com.example.inventory.inventory;

import java.util.UUID;

public record InventorySettingResponse(
        UUID warehouseId,
        UUID productId,
        String sku,
        String name,
        int minimumStock,
        boolean active) {

    static InventorySettingResponse from(InventorySettingProjection setting) {
        return new InventorySettingResponse(
                setting.getWarehouseId(), setting.getProductId(),
                setting.getSku(), setting.getName(),
                setting.getMinimumStock(), setting.getActive());
    }
}
