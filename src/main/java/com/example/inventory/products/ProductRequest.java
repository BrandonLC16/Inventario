package com.example.inventory.products;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @Schema(example = "SKU-001")
        @NotBlank @Size(max = 64) String sku,
        @Schema(example = "Mechanical keyboard")
        @NotBlank @Size(max = 160) String name,
        @Schema(example = "Compact keyboard with tactile switches")
        @Size(max = 1000) String description,
        @Schema(example = "1299.90")
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @Schema(example = "true")
        @NotNull Boolean active) {
}
