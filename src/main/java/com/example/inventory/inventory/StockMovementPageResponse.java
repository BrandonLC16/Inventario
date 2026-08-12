package com.example.inventory.inventory;

import org.springframework.data.domain.Page;

import java.util.List;

public record StockMovementPageResponse(
        List<StockMovementResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    static StockMovementPageResponse from(Page<StockMovement> result) {
        return new StockMovementPageResponse(
                result.getContent().stream().map(StockMovementResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isFirst(), result.isLast());
    }
}
