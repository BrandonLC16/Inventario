package com.example.inventory.counts;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inventory_counts")
class InventoryCount {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String folio;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private InventoryCountStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InventoryCountScope scope;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "opened_by", length = 255)
    private String openedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "submitted_by", length = 255)
    private String submittedBy;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "posted_by", length = 255)
    private String postedBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by", length = 255)
    private String cancelledBy;

    @OneToMany(mappedBy = "inventoryCount", cascade = CascadeType.ALL,
            orphanRemoval = true)
    @OrderBy("productId ASC")
    @BatchSize(size = 200)
    private List<InventoryCountLine> lines = new ArrayList<>();

    protected InventoryCount() {
    }

    InventoryCount(String folio, UUID warehouseId, InventoryCountScope scope,
                   List<UUID> productIds) {
        id = UUID.randomUUID();
        status = InventoryCountStatus.DRAFT;
        this.folio = folio;
        this.warehouseId = warehouseId;
        this.scope = scope;
        productIds.forEach(productId ->
                lines.add(new InventoryCountLine(this, productId)));
    }

    void open(String actor) {
        status = InventoryCountStatus.OPEN;
        openedAt = Instant.now();
        openedBy = actor;
    }

    void submit(String actor) {
        status = InventoryCountStatus.SUBMITTED;
        submittedAt = Instant.now();
        submittedBy = actor;
    }

    void post(String actor) {
        status = InventoryCountStatus.POSTED;
        postedAt = Instant.now();
        postedBy = actor;
    }

    void cancel(String actor) {
        status = InventoryCountStatus.CANCELLED;
        cancelledAt = Instant.now();
        cancelledBy = actor;
    }

    UUID getId() { return id; }
    String getFolio() { return folio; }
    UUID getWarehouseId() { return warehouseId; }
    InventoryCountStatus getStatus() { return status; }
    InventoryCountScope getScope() { return scope; }
    Instant getOpenedAt() { return openedAt; }
    String getOpenedBy() { return openedBy; }
    Instant getSubmittedAt() { return submittedAt; }
    String getSubmittedBy() { return submittedBy; }
    Instant getPostedAt() { return postedAt; }
    String getPostedBy() { return postedBy; }
    Instant getCancelledAt() { return cancelledAt; }
    String getCancelledBy() { return cancelledBy; }
    List<InventoryCountLine> getLines() { return List.copyOf(lines); }
}
