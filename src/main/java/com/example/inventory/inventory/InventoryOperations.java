package com.example.inventory.inventory;

import java.util.UUID;

/** Public write contract exposed by inventory to business modules. */
public interface InventoryOperations {

    void reserveForOrder(UUID productId, int quantity, UUID orderId,
                         String responsibleUser);

    void releaseForOrder(UUID productId, UUID orderId, String responsibleUser);

    void consumeReservation(UUID productId, int quantity, UUID orderId,
                            String responsibleUser);

    void restoreForOrder(UUID productId, int quantity, UUID orderId, String responsibleUser);
}
