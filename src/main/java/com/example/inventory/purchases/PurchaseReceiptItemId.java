package com.example.inventory.purchases;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
class PurchaseReceiptItemId implements Serializable {

    @Column(name = "receipt_id", nullable = false)
    private UUID receiptId;

    @Column(name = "purchase_order_item_id", nullable = false)
    private UUID purchaseOrderItemId;

    protected PurchaseReceiptItemId() {
    }

    PurchaseReceiptItemId(UUID receiptId, UUID purchaseOrderItemId) {
        this.receiptId = receiptId;
        this.purchaseOrderItemId = purchaseOrderItemId;
    }

    UUID getReceiptId() { return receiptId; }
    UUID getPurchaseOrderItemId() { return purchaseOrderItemId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PurchaseReceiptItemId that)) return false;
        return Objects.equals(receiptId, that.receiptId)
                && Objects.equals(purchaseOrderItemId, that.purchaseOrderItemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(receiptId, purchaseOrderItemId);
    }
}
