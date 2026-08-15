package com.example.inventory.purchases;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_items")
class PurchaseOrderItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "supplier_sku", length = 64)
    private String supplierSku;

    @Column(name = "ordered_quantity", nullable = false)
    private int orderedQuantity;

    @Column(name = "received_quantity", nullable = false)
    private int receivedQuantity;

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal unitCost;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal subtotal;

    protected PurchaseOrderItem() {
    }

    PurchaseOrderItem(PurchaseOrder purchaseOrder, PurchaseOrderLine line) {
        id = UUID.randomUUID();
        this.purchaseOrder = purchaseOrder;
        receivedQuantity = 0;
        update(line);
    }

    void update(PurchaseOrderLine line) {
        productId = line.productId();
        supplierSku = line.supplierSku();
        orderedQuantity = line.orderedQuantity();
        unitCost = line.unitCost();
        subtotal = unitCost.multiply(BigDecimal.valueOf(orderedQuantity));
    }

    void receive(int quantity) {
        int updated = Math.addExact(receivedQuantity, quantity);
        if (quantity <= 0 || updated > orderedQuantity) {
            throw new IllegalArgumentException(
                    "Received quantity must be positive and cannot exceed pending quantity");
        }
        receivedQuantity = updated;
    }

    int pendingQuantity() {
        return orderedQuantity - receivedQuantity;
    }

    UUID getId() { return id; }
    UUID getProductId() { return productId; }
    String getSupplierSku() { return supplierSku; }
    int getOrderedQuantity() { return orderedQuantity; }
    int getReceivedQuantity() { return receivedQuantity; }
    BigDecimal getUnitCost() { return unitCost; }
    BigDecimal getSubtotal() { return subtotal; }
}
