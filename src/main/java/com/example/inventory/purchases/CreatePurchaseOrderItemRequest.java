package com.example.inventory.purchases;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreatePurchaseOrderItemRequest(
        @NotNull UUID productId,
        @Size(max = 64) String supplierSku,
        @Positive int orderedQuantity,
        @NotNull @DecimalMin("0.0000") @Digits(integer = 10, fraction = 4)
        BigDecimal unitCost) {
}
