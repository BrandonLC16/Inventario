package com.example.inventory.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory")
class InventoryItem {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryItem() {
    }

    InventoryItem(UUID productId) {
        this.productId = productId;
        this.quantity = 0;
    }

    void changeQuantity(int delta) {
        int newQuantity;
        try {
            newQuantity = Math.addExact(quantity, delta);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Inventory quantity is outside the supported range", exception);
        }
        if (newQuantity < 0) {
            throw new IllegalArgumentException("Inventory quantity cannot be negative");
        }
        quantity = newQuantity;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    UUID getProductId() { return productId; }
    int getQuantity() { return quantity; }
    Instant getUpdatedAt() { return updatedAt; }
}
