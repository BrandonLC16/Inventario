package com.example.inventory.counts;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateInventoryCountLineRequest(
        @NotNull @PositiveOrZero Integer countedQuantity,
        @Size(max = 1000) String notes) {
}
