package com.example.inventory.purchases;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreatePurchaseOrderRequest(
        @NotNull UUID supplierId,
        @NotNull UUID destinationWarehouseId,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @Size(max = 128) String supplierReference,
        @NotEmpty @Size(max = 100)
        List<@Valid CreatePurchaseOrderItemRequest> items) {

    public CreatePurchaseOrderRequest {
        if (items != null) items = List.copyOf(items);
    }
}
