package com.example.inventory.inventory;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record InventorySettingRequest(
        @PositiveOrZero int minimumStock,
        @NotNull Boolean active) { }
