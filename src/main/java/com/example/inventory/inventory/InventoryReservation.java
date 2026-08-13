package com.example.inventory.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_reservations")
class InventoryReservation {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "reserved_at", nullable = false)
    private Instant reservedAt;

    @Column(name = "reserved_by", nullable = false, length = 255)
    private String reservedBy;

    protected InventoryReservation() {
    }

    InventoryReservation(UUID orderId, UUID productId, int quantity,
                         String reservedBy) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.reservedAt = Instant.now();
        this.reservedBy = reservedBy;
    }

    UUID getOrderId() { return orderId; }
    UUID getProductId() { return productId; }
    int getQuantity() { return quantity; }
}
