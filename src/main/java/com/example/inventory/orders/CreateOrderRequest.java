package com.example.inventory.orders;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        UUID customerId,
        UUID fulfillmentWarehouseId,
        @NotEmpty @Size(max = 100) List<@Valid CreateOrderItemRequest> items) {

    public CreateOrderRequest(List<CreateOrderItemRequest> items) {
        this(null, null, items);
    }

    public CreateOrderRequest(UUID customerId, List<CreateOrderItemRequest> items) {
        this(customerId, null, items);
    }
}
