package com.example.inventory.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_movements")
class StockMovement {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 32)
    private StockMovementType movementType;

    @Column(name = "quantity_delta", nullable = false)
    private int quantityDelta;

    @Column(name = "balance_before", nullable = false)
    private int balanceBefore;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(name = "business_reference", nullable = false, length = 128)
    private String businessReference;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "responsible_user", length = 255)
    private String responsibleUser;

    protected StockMovement() {
    }

    StockMovement(UUID productId, StockMovementType movementType, int quantityDelta,
                  int balanceBefore, int balanceAfter, String businessReference,
                  String responsibleUser) {
        this.id = UUID.randomUUID();
        this.productId = productId;
        this.movementType = movementType;
        this.quantityDelta = quantityDelta;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.businessReference = businessReference;
        this.occurredAt = Instant.now();
        this.responsibleUser = responsibleUser;
    }

    UUID getId() { return id; }
    UUID getProductId() { return productId; }
    StockMovementType getMovementType() { return movementType; }
    int getQuantityDelta() { return quantityDelta; }
    int getBalanceBefore() { return balanceBefore; }
    int getBalanceAfter() { return balanceAfter; }
    String getBusinessReference() { return businessReference; }
    Instant getOccurredAt() { return occurredAt; }
    String getResponsibleUser() { return responsibleUser; }
}
