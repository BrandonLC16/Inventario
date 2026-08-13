package com.example.inventory.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface InventoryReservationRepository extends JpaRepository<InventoryReservation, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select reservation from InventoryReservation reservation
            where reservation.orderId = :orderId
              and reservation.warehouseId = :warehouseId
              and reservation.productId = :productId
            """)
    Optional<InventoryReservation> findForUpdate(
            @Param("orderId") UUID orderId,
            @Param("warehouseId") UUID warehouseId,
            @Param("productId") UUID productId);

    @Query("""
            select coalesce(sum(reservation.quantity), 0)
            from InventoryReservation reservation
            where reservation.warehouseId = :warehouseId
              and reservation.productId = :productId
            """)
    long reservedQuantity(@Param("warehouseId") UUID warehouseId,
                          @Param("productId") UUID productId);
}
