package com.example.inventory.inventory;

import java.util.UUID;

/** Public write contract exposed by inventory to business modules. */
public interface InventoryOperations {
    void reserveForOrder(UUID warehouseId, UUID productId, int quantity,
                         UUID orderId, String responsibleUser);
    void releaseForOrder(UUID warehouseId, UUID productId,
                         UUID orderId, String responsibleUser);
    void consumeReservation(UUID warehouseId, UUID productId, int quantity,
                            UUID orderId, String responsibleUser);
    void restoreForOrder(UUID warehouseId, UUID productId, int quantity,
                         UUID orderId, String responsibleUser);
    void receivePurchase(UUID warehouseId, UUID productId, int quantity,
                         UUID receiptId, String responsibleUser);
    void transferOut(UUID warehouseId, UUID productId, int quantity,
                     UUID transferId, String responsibleUser);
    void transferIn(UUID warehouseId, UUID productId, int quantity,
                    UUID transferId, String responsibleUser);
}
