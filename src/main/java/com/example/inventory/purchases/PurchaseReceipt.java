package com.example.inventory.purchases;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "purchase_receipts")
class PurchaseReceipt {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String folio;

    @Column(name = "purchase_order_id", nullable = false)
    private UUID purchaseOrderId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "external_reference", nullable = false, length = 128)
    private String externalReference;

    @Column(name = "update_supplier_product_last_cost")
    private Boolean updateSupplierProductLastCost;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "received_by", nullable = false, length = 255)
    private String receivedBy;

    protected PurchaseReceipt() {
    }

    PurchaseReceipt(String folio, UUID purchaseOrderId, UUID warehouseId,
                    String externalReference,
                    boolean updateSupplierProductLastCost,
                    String receivedBy) {
        id = UUID.randomUUID();
        this.folio = folio;
        this.purchaseOrderId = purchaseOrderId;
        this.warehouseId = warehouseId;
        this.externalReference = externalReference;
        this.updateSupplierProductLastCost = updateSupplierProductLastCost;
        receivedAt = Instant.now();
        this.receivedBy = receivedBy;
    }

    UUID getId() { return id; }
    String getFolio() { return folio; }
    UUID getPurchaseOrderId() { return purchaseOrderId; }
    UUID getWarehouseId() { return warehouseId; }
    String getExternalReference() { return externalReference; }
    Boolean getUpdateSupplierProductLastCost() {
        return updateSupplierProductLastCost;
    }
    Instant getReceivedAt() { return receivedAt; }
    String getReceivedBy() { return receivedBy; }
}
