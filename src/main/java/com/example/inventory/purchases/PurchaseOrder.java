package com.example.inventory.purchases;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "purchase_orders")
class PurchaseOrder {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String folio;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "destination_warehouse_id", nullable = false)
    private UUID destinationWarehouseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PurchaseOrderStatus status;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal total;

    @Column(name = "supplier_reference", length = 128)
    private String supplierReference;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "issued_by", length = 255)
    private String issuedBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by", length = 255)
    private String cancelledBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL,
            orphanRemoval = true)
    @OrderBy("productId ASC")
    @BatchSize(size = 100)
    private List<PurchaseOrderItem> items = new ArrayList<>();

    protected PurchaseOrder() {
    }

    PurchaseOrder(String folio, UUID supplierId, UUID destinationWarehouseId,
                  String currency, String supplierReference, String createdBy,
                  List<PurchaseOrderLine> lines) {
        id = UUID.randomUUID();
        status = PurchaseOrderStatus.DRAFT;
        this.folio = folio;
        this.supplierId = supplierId;
        this.destinationWarehouseId = destinationWarehouseId;
        this.currency = currency;
        this.supplierReference = supplierReference;
        this.createdBy = createdBy;
        replaceItems(lines);
    }

    void changeDestinationWarehouse(UUID warehouseId) {
        destinationWarehouseId = warehouseId;
    }

    void replaceItems(List<PurchaseOrderLine> lines) {
        var productIds = lines.stream().map(PurchaseOrderLine::productId)
                .collect(Collectors.toSet());
        items.removeIf(item -> !productIds.contains(item.getProductId()));
        lines.forEach(line -> items.stream()
                .filter(item -> item.getProductId().equals(line.productId()))
                .findFirst()
                .ifPresentOrElse(
                        item -> item.update(line),
                        () -> items.add(new PurchaseOrderItem(this, line))));
        total = items.stream().map(PurchaseOrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    void issue(String actor) {
        status = PurchaseOrderStatus.ISSUED;
        issuedAt = Instant.now();
        issuedBy = actor;
    }

    void recordReceipt(boolean complete) {
        status = complete
                ? PurchaseOrderStatus.RECEIVED
                : PurchaseOrderStatus.PARTIALLY_RECEIVED;
    }

    void cancel(String actor) {
        status = PurchaseOrderStatus.CANCELLED;
        cancelledAt = Instant.now();
        cancelledBy = actor;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    UUID getId() { return id; }
    String getFolio() { return folio; }
    UUID getSupplierId() { return supplierId; }
    UUID getDestinationWarehouseId() { return destinationWarehouseId; }
    PurchaseOrderStatus getStatus() { return status; }
    String getCurrency() { return currency; }
    BigDecimal getTotal() { return total; }
    String getSupplierReference() { return supplierReference; }
    Instant getIssuedAt() { return issuedAt; }
    String getIssuedBy() { return issuedBy; }
    Instant getCancelledAt() { return cancelledAt; }
    String getCancelledBy() { return cancelledBy; }
    Instant getCreatedAt() { return createdAt; }
    String getCreatedBy() { return createdBy; }
    Instant getUpdatedAt() { return updatedAt; }
    List<PurchaseOrderItem> getItems() { return List.copyOf(items); }
}
