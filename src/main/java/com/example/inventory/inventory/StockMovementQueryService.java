package com.example.inventory.inventory;

import com.example.inventory.products.ProductCatalog;
import com.example.inventory.shared.BadRequestException;
import com.example.inventory.warehouses.WarehouseDirectory;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class StockMovementQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final StockMovementRepository movements;
    private final ProductCatalog products;
    private final WarehouseDirectory warehouses;

    StockMovementQueryService(StockMovementRepository movements, ProductCatalog products,
                              WarehouseDirectory warehouses) {
        this.movements = movements;
        this.products = products;
        this.warehouses = warehouses;
    }

    StockMovementPageResponse findMovements(UUID warehouseId, UUID productId, int page, int size,
                                            StockMovementType type, Instant from,
                                            Instant to, String reference) {
        validatePage(page, size);
        validateDateRange(from, to);
        String normalizedReference = normalizeReference(reference);
        warehouses.requireWarehouse(warehouseId);
        if (productId != null) {
            products.requireStoredProduct(productId);
        }

        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "occurredAt")
                        .and(Sort.by(Sort.Direction.DESC, "id")));
        return StockMovementPageResponse.from(movements.findAll(
                filters(warehouseId, productId, type, from, to, normalizedReference), pageable));
    }

    private Specification<StockMovement> filters(UUID warehouseId, UUID productId, StockMovementType type,
                                                  Instant from, Instant to,
                                                  String reference) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("warehouseId"), warehouseId));
            if (productId != null) {
                predicates.add(builder.equal(root.get("productId"), productId));
            }
            if (type != null) {
                predicates.add(builder.equal(root.get("movementType"), type));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("occurredAt"), to));
            }
            if (reference != null) {
                predicates.add(builder.equal(root.get("businessReference"), reference));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("Page index must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("Page size must be between 1 and 100");
        }
    }

    private void validateDateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("The from date must not be after the to date");
        }
    }

    private String normalizeReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        String normalized = reference.trim();
        if (normalized.length() > 128) {
            throw new BadRequestException("Reference must not exceed 128 characters");
        }
        return normalized;
    }
}
