package com.example.inventory.transfers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateInventoryTransferRequest(
        @NotNull UUID sourceWarehouseId,
        @NotNull UUID destinationWarehouseId,
        @NotEmpty @Size(max = 100)
        List<@Valid CreateInventoryTransferItemRequest> items) {

    public CreateInventoryTransferRequest {
        if (items != null) items = List.copyOf(items);
    }
}
