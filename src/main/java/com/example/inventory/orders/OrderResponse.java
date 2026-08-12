package com.example.inventory.orders;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

public record OrderResponse(
        UUID id,
        String folio,
        UUID customerId,
        OrderStatus status,
        String currency,
        BigDecimal total,
        List<OrderItemResponse> items,
        String createdBy,
        String confirmedBy,
        String cancelledBy,
        Instant createdAt,
        Instant updatedAt,
        Instant confirmedAt,
        Instant cancelledAt) {

    static OrderResponse from(SalesOrder order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .sorted(Comparator.comparing(OrderItem::getProductId))
                .map(OrderItemResponse::from)
                .toList();
        return new OrderResponse(order.getId(), order.getFolio(), order.getCustomerId(),
                order.getStatus(), order.getCurrency(), order.getTotal(), items,
                order.getCreatedBy(), order.getConfirmedBy(), order.getCancelledBy(),
                order.getCreatedAt(), order.getUpdatedAt(),
                order.getConfirmedAt(), order.getCancelledAt());
    }
}
