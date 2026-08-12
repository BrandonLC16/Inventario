package com.example.inventory.orders;

import java.util.UUID;
import java.math.BigDecimal;

public record OrderItemResponse(
        UUID id,
        UUID productId,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal) {

    static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getId(), item.getProductId(), item.getQuantity(),
                item.getUnitPrice(), item.getSubtotal());
    }
}
