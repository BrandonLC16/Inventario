package com.example.inventory.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

interface StockMovementRepository extends JpaRepository<StockMovement, UUID>,
        JpaSpecificationExecutor<StockMovement> {

    @Query("select coalesce(sum(movement.quantityDelta), 0) from StockMovement movement "
            + "where movement.warehouseId = :warehouseId "
            + "and movement.productId = :productId "
            + "and movement.occurredAt > :after "
            + "and movement.occurredAt <= :through")
    long quantityDeltaBetween(
            @Param("warehouseId") UUID warehouseId,
            @Param("productId") UUID productId,
            @Param("after") Instant after,
            @Param("through") Instant through);
}
