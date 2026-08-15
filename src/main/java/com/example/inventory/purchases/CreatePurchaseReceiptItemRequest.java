package com.example.inventory.purchases;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record CreatePurchaseReceiptItemRequest(
        @NotNull UUID purchaseOrderItemId,
        @Positive @Max(CreatePurchaseOrderItemRequest.MAX_QUANTITY) int quantity,
        @NotNull @DecimalMin("0.0000") @Digits(integer = 10, fraction = 4)
        BigDecimal unitCost) {
}
