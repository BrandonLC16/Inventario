package com.example.inventory.transfers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record UpdateInventoryTransferItemsRequest(
        UUID sourceWarehouseId,
        UUID destinationWarehouseId,
        @NotEmpty @Size(max = 100)
        List<@Valid CreateInventoryTransferItemRequest> items) {

    public UpdateInventoryTransferItemsRequest {
        if (items != null) items = List.copyOf(items);
    }
}
