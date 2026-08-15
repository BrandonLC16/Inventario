package com.example.inventory.purchases;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreatePurchaseReceiptRequest(
        @NotBlank @Size(max = 128) String externalReference,
        @JsonAlias({"updateLastCost", "updateLastUnitCost",
                "updateSupplierLastCost", "updateSupplierProductLastUnitCost"})
        boolean updateSupplierProductLastCost,
        @NotEmpty @Size(max = 100)
        List<@Valid CreatePurchaseReceiptItemRequest> items) {

    public CreatePurchaseReceiptRequest {
        if (items != null) items = List.copyOf(items);
    }
}
