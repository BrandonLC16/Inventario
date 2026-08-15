package com.example.inventory.purchases;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface PurchaseOrderItemRepository
        extends JpaRepository<PurchaseOrderItem, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from PurchaseOrderItem item "
            + "where item.purchaseOrder.id = :purchaseOrderId "
            + "order by item.productId")
    List<PurchaseOrderItem> findByPurchaseOrderIdForUpdate(
            @Param("purchaseOrderId") UUID purchaseOrderId);
}
