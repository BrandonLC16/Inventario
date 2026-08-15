package com.example.inventory.counts;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateInventoryCountRequest(
        @NotNull UUID warehouseId,
        @NotNull InventoryCountScope scope,
        @Size(max = 1000) List<@NotNull UUID> productIds) {

    public CreateInventoryCountRequest {
        if (productIds != null) productIds = List.copyOf(productIds);
    }
}
