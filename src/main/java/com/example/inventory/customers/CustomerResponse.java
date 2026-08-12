package com.example.inventory.customers;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String name,
        String fiscalIdentifier,
        String email,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(), customer.getName(), customer.getFiscalIdentifier(),
                customer.getEmail(), customer.isActive(),
                customer.getCreatedAt(), customer.getUpdatedAt());
    }
}
