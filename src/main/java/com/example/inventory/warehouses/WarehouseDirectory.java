package com.example.inventory.warehouses;

import java.util.List;
import java.util.UUID;

/** Public contract exposed by warehouses to transactional business modules. */
public interface WarehouseDirectory {
    UUID MAIN_WAREHOUSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    void requireWarehouse(UUID warehouseId);
    void lockWarehouse(UUID warehouseId);
    /** Serializes product and warehouse registration before either catalog row is inserted. */
    void lockCatalogRegistration();
    void lockActiveWarehouse(UUID warehouseId);
    void requireActiveProduct(UUID warehouseId, UUID productId);
    void ensureProductCanBeDeleted(UUID productId);
    void registerProduct(UUID productId);
    void configureProduct(UUID warehouseId, UUID productId, int minimumStock, boolean active);
    List<UUID> productIdsForPhysicalCount(UUID warehouseId, int maximumResults);
}
