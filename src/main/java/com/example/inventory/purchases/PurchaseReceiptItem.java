package com.example.inventory.purchases;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_receipt_items")
class PurchaseReceiptItem {

    @EmbeddedId
    private PurchaseReceiptItemId id;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal unitCost;

    protected PurchaseReceiptItem() {
    }

    PurchaseReceiptItem(UUID receiptId, UUID purchaseOrderItemId,
                        int quantity, BigDecimal unitCost) {
        id = new PurchaseReceiptItemId(receiptId, purchaseOrderItemId);
        this.quantity = quantity;
        this.unitCost = unitCost;
    }

    UUID getReceiptId() { return id.getReceiptId(); }
    UUID getPurchaseOrderItemId() { return id.getPurchaseOrderItemId(); }
    int getQuantity() { return quantity; }
    BigDecimal getUnitCost() { return unitCost; }
}
