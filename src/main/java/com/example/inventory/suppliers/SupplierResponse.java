package com.example.inventory.suppliers;

import java.time.Instant;
import java.util.UUID;

public record SupplierResponse(
        UUID id,
        String code,
        String legalName,
        String commercialName,
        String fiscalIdentifier,
        String email,
        String phone,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    static SupplierResponse from(Supplier supplier) {
        return new SupplierResponse(
                supplier.getId(), supplier.getCode(), supplier.getLegalName(),
                supplier.getCommercialName(), supplier.getFiscalIdentifier(),
                supplier.getEmail(), supplier.getPhone(), supplier.isActive(),
                supplier.getCreatedAt(), supplier.getUpdatedAt());
    }
}
