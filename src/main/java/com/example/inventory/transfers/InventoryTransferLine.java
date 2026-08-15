package com.example.inventory.transfers;

import java.util.UUID;

record InventoryTransferLine(UUID productId, int quantity) {
}
