package com.example.inventory.suppliers;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SupplierProductRequest(
        @Size(max = 64) String supplierSku,
        @NotNull @PositiveOrZero Integer leadTimeDays,
        @NotNull @Positive Integer minimumOrderQuantity,
        @DecimalMin("0.0000") @Digits(integer = 10, fraction = 4) BigDecimal lastUnitCost,
        @NotNull Boolean preferred,
        @NotNull Boolean active) {
}
