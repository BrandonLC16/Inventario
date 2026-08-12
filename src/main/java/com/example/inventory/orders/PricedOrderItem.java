package com.example.inventory.orders;

import java.math.BigDecimal;
import java.util.UUID;

record PricedOrderItem(UUID productId, int quantity, BigDecimal unitPrice) {
}
