package com.example.inventory.orders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "order_items")
class OrderItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private SalesOrder order;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    protected OrderItem() {
    }

    OrderItem(SalesOrder order, UUID productId, int quantity) {
        this.id = UUID.randomUUID();
        this.order = order;
        this.productId = productId;
        this.quantity = quantity;
    }

    UUID getId() { return id; }
    UUID getProductId() { return productId; }
    int getQuantity() { return quantity; }
}
