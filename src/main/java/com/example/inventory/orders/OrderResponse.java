package com.example.inventory.orders;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        OrderStatus status,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt) {

    static OrderResponse from(SalesOrder order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .sorted(Comparator.comparing(OrderItem::getProductId))
                .map(OrderItemResponse::from)
                .toList();
        return new OrderResponse(order.getId(), order.getStatus(), items,
                order.getCreatedAt(), order.getUpdatedAt());
    }
}
