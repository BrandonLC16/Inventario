package com.example.inventory.counts;

import com.example.inventory.inventory.PhysicalCountSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_count_lines")
class InventoryCountLine {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "count_id", nullable = false)
    private InventoryCount inventoryCount;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "expected_quantity")
    private Integer expectedQuantity;

    @Column(name = "counted_quantity")
    private Integer countedQuantity;

    private Integer variance;

    @Column(name = "counted_at")
    private Instant countedAt;

    @Column(name = "counted_by", length = 255)
    private String countedBy;

    @Column(length = 1000)
    private String notes;

    protected InventoryCountLine() {
    }

    InventoryCountLine(InventoryCount count, UUID productId) {
        id = UUID.randomUUID();
        this.inventoryCount = count;
        this.productId = productId;
    }

    void open(int expectedQuantity) {
        this.expectedQuantity = expectedQuantity;
    }

    void capture(PhysicalCountSnapshot snapshot, int quantity,
                 String actor, String notes) {
        expectedQuantity = snapshot.expectedQuantity();
        countedQuantity = quantity;
        variance = (int) ((long) quantity - expectedQuantity);
        countedAt = snapshot.capturedAt();
        countedBy = actor;
        this.notes = notes;
    }

    Instant previousSnapshotAt(Instant openedAt) {
        return countedAt == null ? openedAt : countedAt;
    }

    UUID getId() { return id; }
    UUID getProductId() { return productId; }
    Integer getExpectedQuantity() { return expectedQuantity; }
    Integer getCountedQuantity() { return countedQuantity; }
    Integer getVariance() { return variance; }
    Instant getCountedAt() { return countedAt; }
    String getCountedBy() { return countedBy; }
    String getNotes() { return notes; }
}
