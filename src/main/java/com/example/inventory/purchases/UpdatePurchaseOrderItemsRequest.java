package com.example.inventory.purchases;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record UpdatePurchaseOrderItemsRequest(
        UUID destinationWarehouseId,
        @NotEmpty @Size(max = 100)
        List<@Valid CreatePurchaseOrderItemRequest> items) {

    public UpdatePurchaseOrderItemsRequest {
        if (items != null) items = List.copyOf(items);
    }
}
