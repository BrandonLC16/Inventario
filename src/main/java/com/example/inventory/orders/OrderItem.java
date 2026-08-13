package com.example.inventory.orders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;
import java.math.BigDecimal;

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

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal subtotal;

    protected OrderItem() {
    }

    OrderItem(SalesOrder order, UUID productId, int quantity, BigDecimal unitPrice) {
        this.id = UUID.randomUUID();
        this.order = order;
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    void update(int quantity, BigDecimal unitPrice) {
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    UUID getId() { return id; }
    UUID getProductId() { return productId; }
    int getQuantity() { return quantity; }
    BigDecimal getUnitPrice() { return unitPrice; }
    BigDecimal getSubtotal() { return subtotal; }
}
