package com.example.inventory.transfers;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "inventory_transfer_items")
class InventoryTransferItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false)
    private InventoryTransfer transfer;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    protected InventoryTransferItem() {
    }

    InventoryTransferItem(InventoryTransfer transfer, InventoryTransferLine line) {
        id = UUID.randomUUID();
        this.transfer = transfer;
        update(line);
    }

    void update(InventoryTransferLine line) {
        productId = line.productId();
        quantity = line.quantity();
    }

    UUID getId() { return id; }
    UUID getProductId() { return productId; }
    int getQuantity() { return quantity; }
}
