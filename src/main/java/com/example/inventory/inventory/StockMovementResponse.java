package com.example.inventory.inventory;

import java.time.Instant;
import java.util.UUID;

public record StockMovementResponse(
        UUID id,
        UUID productId,
        StockMovementType movementType,
        int quantityDelta,
        int balanceBefore,
        int balanceAfter,
        String businessReference,
        Instant occurredAt,
        String responsibleUser) {

    static StockMovementResponse from(StockMovement movement) {
        return new StockMovementResponse(
                movement.getId(), movement.getProductId(), movement.getMovementType(),
                movement.getQuantityDelta(), movement.getBalanceBefore(),
                movement.getBalanceAfter(), movement.getBusinessReference(),
                movement.getOccurredAt(), movement.getResponsibleUser());
    }
}
