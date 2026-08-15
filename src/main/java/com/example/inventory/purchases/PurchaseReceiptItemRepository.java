package com.example.inventory.purchases;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface PurchaseReceiptItemRepository
        extends JpaRepository<PurchaseReceiptItem, PurchaseReceiptItemId> {

    @Query("select item from PurchaseReceiptItem item "
            + "where item.id.receiptId = :receiptId "
            + "order by item.id.purchaseOrderItemId")
    List<PurchaseReceiptItem> findByReceiptId(@Param("receiptId") UUID receiptId);

    @Query("select item from PurchaseReceiptItem item "
            + "where item.id.receiptId in :receiptIds "
            + "order by item.id.receiptId, item.id.purchaseOrderItemId")
    List<PurchaseReceiptItem> findByReceiptIds(
            @Param("receiptIds") Collection<UUID> receiptIds);
}
