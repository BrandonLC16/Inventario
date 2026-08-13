package com.example.inventory.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory")
class InventoryItem {
    @EmbeddedId private InventoryId id;
    @Column(nullable = false) private int quantity;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected InventoryItem() { }

    InventoryItem(UUID warehouseId, UUID productId) {
        this.id = new InventoryId(warehouseId, productId);
        this.quantity = 0;
    }

    void changeQuantity(int delta) {
        int newQuantity;
        try {
            newQuantity = Math.addExact(quantity, delta);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Inventory quantity is outside the supported range", exception);
        }
        if (newQuantity < 0) {
            throw new IllegalArgumentException("Inventory quantity cannot be negative");
        }
        quantity = newQuantity;
    }

    @PrePersist
    @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    UUID getWarehouseId() { return id.getWarehouseId(); }
    UUID getProductId() { return id.getProductId(); }
    int getQuantity() { return quantity; }
    Instant getUpdatedAt() { return updatedAt; }
}
