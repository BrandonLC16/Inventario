package com.example.inventory.inventory;

import java.util.UUID;

/** Public write contract exposed by inventory to business modules. */
public interface InventoryOperations {

    void consumeForOrder(UUID productId, int quantity, UUID orderId, String responsibleUser);

    void restoreForOrder(UUID productId, int quantity, UUID orderId, String responsibleUser);
}
