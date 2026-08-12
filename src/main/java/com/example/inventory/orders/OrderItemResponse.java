package com.example.inventory.orders;

import java.util.UUID;

public record OrderItemResponse(UUID id, UUID productId, int quantity) {

    static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getId(), item.getProductId(), item.getQuantity());
    }
}
