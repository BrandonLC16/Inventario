package com.example.inventory.transfers;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface InventoryTransferItemRepository
        extends JpaRepository<InventoryTransferItem, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from InventoryTransferItem item "
            + "where item.transfer.id = :transferId order by item.productId")
    List<InventoryTransferItem> findByTransferIdForUpdate(
            @Param("transferId") UUID transferId);
}
