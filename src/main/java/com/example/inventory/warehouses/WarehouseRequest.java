package com.example.inventory.warehouses;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WarehouseRequest(
        @Schema(example = "NORTH") @NotBlank @Size(max = 32) String code,
        @Schema(example = "North distribution center")
        @NotBlank @Size(max = 160) String name,
        @Size(max = 1000) String description,
        @NotNull Boolean active) { }
