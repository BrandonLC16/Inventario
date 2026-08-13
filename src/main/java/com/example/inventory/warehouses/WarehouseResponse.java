package com.example.inventory.warehouses;

import java.time.Instant;
import java.util.UUID;

public record WarehouseResponse(
        UUID id, String code, String name, String description, boolean active,
        Instant createdAt, Instant updatedAt) {
    static WarehouseResponse from(Warehouse warehouse) {
        return new WarehouseResponse(
                warehouse.getId(), warehouse.getCode(), warehouse.getName(),
                warehouse.getDescription(), warehouse.isActive(),
                warehouse.getCreatedAt(), warehouse.getUpdatedAt());
    }
}
