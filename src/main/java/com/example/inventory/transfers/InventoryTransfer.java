package com.example.inventory.transfers;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "inventory_transfers")
class InventoryTransfer {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String folio;

    @Column(name = "source_warehouse_id", nullable = false)
    private UUID sourceWarehouseId;

    @Column(name = "destination_warehouse_id", nullable = false)
    private UUID destinationWarehouseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private InventoryTransferStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "dispatched_by", length = 255)
    private String dispatchedBy;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "received_by", length = 255)
    private String receivedBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by", length = 255)
    private String cancelledBy;

    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL,
            orphanRemoval = true)
    @OrderBy("productId ASC")
    @BatchSize(size = 100)
    private List<InventoryTransferItem> items = new ArrayList<>();

    protected InventoryTransfer() {
    }

    InventoryTransfer(String folio, UUID sourceWarehouseId,
                      UUID destinationWarehouseId, String createdBy,
                      List<InventoryTransferLine> lines) {
        id = UUID.randomUUID();
        status = InventoryTransferStatus.DRAFT;
        this.folio = folio;
        this.sourceWarehouseId = sourceWarehouseId;
        this.destinationWarehouseId = destinationWarehouseId;
        this.createdBy = createdBy;
        replaceItems(lines);
    }

    void changeWarehouses(UUID sourceId, UUID destinationId) {
        sourceWarehouseId = sourceId;
        destinationWarehouseId = destinationId;
    }

    void replaceItems(List<InventoryTransferLine> lines) {
        var productIds = lines.stream().map(InventoryTransferLine::productId)
                .collect(Collectors.toSet());
        items.removeIf(item -> !productIds.contains(item.getProductId()));
        lines.forEach(line -> items.stream()
                .filter(item -> item.getProductId().equals(line.productId()))
                .findFirst()
                .ifPresentOrElse(
                        item -> item.update(line),
                        () -> items.add(new InventoryTransferItem(this, line))));
    }

    void dispatch(String actor) {
        status = InventoryTransferStatus.IN_TRANSIT;
        dispatchedAt = Instant.now();
        dispatchedBy = actor;
    }

    void receive(String actor) {
        status = InventoryTransferStatus.RECEIVED;
        receivedAt = Instant.now();
        receivedBy = actor;
    }

    void cancel(String actor) {
        status = InventoryTransferStatus.CANCELLED;
        cancelledAt = Instant.now();
        cancelledBy = actor;
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
    }

    UUID getId() { return id; }
    String getFolio() { return folio; }
    UUID getSourceWarehouseId() { return sourceWarehouseId; }
    UUID getDestinationWarehouseId() { return destinationWarehouseId; }
    InventoryTransferStatus getStatus() { return status; }
    Instant getCreatedAt() { return createdAt; }
    String getCreatedBy() { return createdBy; }
    Instant getDispatchedAt() { return dispatchedAt; }
    String getDispatchedBy() { return dispatchedBy; }
    Instant getReceivedAt() { return receivedAt; }
    String getReceivedBy() { return receivedBy; }
    Instant getCancelledAt() { return cancelledAt; }
    String getCancelledBy() { return cancelledBy; }
    List<InventoryTransferItem> getItems() { return List.copyOf(items); }
}
