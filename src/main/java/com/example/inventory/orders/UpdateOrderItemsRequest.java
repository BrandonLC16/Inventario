package com.example.inventory.orders;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record UpdateOrderItemsRequest(
        UUID fulfillmentWarehouseId,
        @NotEmpty @Size(max = 100) List<@Valid CreateOrderItemRequest> items) {

    public UpdateOrderItemsRequest {
        if (items != null) items = List.copyOf(items);
    }

    public UpdateOrderItemsRequest(List<CreateOrderItemRequest> items) {
        this(null, items);
    }
}
