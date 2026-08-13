package com.example.inventory.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public final class InventoryId implements Serializable {
    @Column(name = "warehouse_id", nullable = false) private UUID warehouseId;
    @Column(name = "product_id", nullable = false) private UUID productId;

    protected InventoryId() { }
    public InventoryId(UUID warehouseId, UUID productId) {
        this.warehouseId = Objects.requireNonNull(warehouseId);
        this.productId = Objects.requireNonNull(productId);
    }
    public UUID getWarehouseId() { return warehouseId; }
    public UUID getProductId() { return productId; }
    @Override public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof InventoryId other)) return false;
        return warehouseId.equals(other.warehouseId) && productId.equals(other.productId);
    }
    @Override public int hashCode() { return Objects.hash(warehouseId, productId); }
}
