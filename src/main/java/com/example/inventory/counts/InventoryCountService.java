package com.example.inventory.counts;

import com.example.inventory.inventory.InventoryOperations;
import com.example.inventory.products.ProductCatalog;
import com.example.inventory.shared.BadRequestException;
import com.example.inventory.shared.ConflictException;
import com.example.inventory.shared.NotFoundException;
import com.example.inventory.shared.PageResponse;
import com.example.inventory.shared.PageSupport;
import com.example.inventory.warehouses.WarehouseDirectory;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_MANAGER')")
@Transactional(readOnly = true)
public class InventoryCountService {

    private static final int MAX_PRODUCTS_PER_COUNT = 1000;

    private final InventoryCountRepository counts;
    private final InventoryCountLineRepository countLines;
    private final ProductCatalog products;
    private final WarehouseDirectory warehouses;
    private final InventoryOperations inventory;

    public InventoryCountService(
            InventoryCountRepository counts,
            InventoryCountLineRepository countLines,
            ProductCatalog products,
            WarehouseDirectory warehouses,
            InventoryOperations inventory) {
        this.counts = counts;
        this.countLines = countLines;
        this.products = products;
        this.warehouses = warehouses;
        this.inventory = inventory;
    }

    public PageResponse<InventoryCountResponse> findAll(
            int page, int size, InventoryCountStatus status,
            InventoryCountScope scope, UUID warehouseId, String folio) {
        var pageable = PageSupport.request(page, size,
                Sort.by(Sort.Direction.DESC, "folio")
                        .and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.from(counts.findAll(
                        filters(status, scope, warehouseId, folio), pageable),
                InventoryCountResponse::from);
    }

    public InventoryCountResponse findById(UUID id) {
        return InventoryCountResponse.from(counts.findDetailedById(id)
                .orElseThrow(() -> notFound(id)));
    }

    @Transactional
    public InventoryCountResponse create(CreateInventoryCountRequest request) {
        if (request == null || request.warehouseId() == null
                || request.scope() == null) {
            throw new BadRequestException(
                    "Warehouse and scope are required for an inventory count");
        }
        warehouses.lockActiveWarehouse(request.warehouseId());
        List<UUID> productIds = resolveProductIds(request);
        if (countLines.existsActiveOverlap(
                request.warehouseId(), productIds)) {
            throw new ConflictException(
                    "An active inventory count already includes one or more requested products");
        }
        String folio = "CNT-%010d".formatted(counts.nextFolioSequence());
        InventoryCount inventoryCount = new InventoryCount(
                folio, request.warehouseId(), request.scope(), productIds);
        return InventoryCountResponse.from(
                counts.saveAndFlush(inventoryCount));
    }

    @Transactional
    public InventoryCountResponse open(UUID id, String responsibleUser) {
        InventoryCount inventoryCount = requireForUpdate(id);
        if (inventoryCount.getStatus() != InventoryCountStatus.DRAFT) {
            throw new ConflictException(
                    "Only a draft inventory count can be opened");
        }
        ensureCountIsBounded(id);
        List<InventoryCountLine> lines = countLines
                .findByCountIdForUpdate(id);
        if (lines.isEmpty()) {
            throw new ConflictException(
                    "An inventory count must contain products before opening");
        }
        warehouses.lockActiveWarehouse(inventoryCount.getWarehouseId());
        for (InventoryCountLine line : lines) {
            int expectedQuantity = inventory
                    .lockAndGetQuantityForPhysicalCount(
                            inventoryCount.getWarehouseId(),
                            line.getProductId());
            line.open(expectedQuantity);
        }
        inventoryCount.open(requireActor(responsibleUser));
        counts.flush();
        return InventoryCountResponse.from(inventoryCount);
    }

    @Transactional
    public InventoryCountResponse updateLine(
            UUID id, UUID productId,
            UpdateInventoryCountLineRequest request, String responsibleUser) {
        InventoryCount inventoryCount = requireForUpdate(id);
        if (inventoryCount.getStatus() != InventoryCountStatus.OPEN) {
            throw new ConflictException(
                    "Only an open inventory count can capture quantities");
        }
        if (request == null || request.countedQuantity() == null
                || request.countedQuantity() < 0) {
            throw new BadRequestException(
                    "Counted quantity must not be negative");
        }
        InventoryCountLine line = countLines
                .findByCountIdAndProductIdForUpdate(id, productId)
                .orElseThrow(() -> new NotFoundException(
                        "Product %s is not part of inventory count %s"
                                .formatted(productId, id)));
        if (line.getExpectedQuantity() == null
                || inventoryCount.getOpenedAt() == null) {
            throw new ConflictException(
                    "The inventory count does not have an opening snapshot");
        }
        var snapshot = inventory.capturePhysicalCountExpectation(
                inventoryCount.getWarehouseId(), productId,
                line.getExpectedQuantity(), line.previousSnapshotAt(
                        inventoryCount.getOpenedAt()));
        line.capture(snapshot, request.countedQuantity(),
                requireActor(responsibleUser), trimToNull(request.notes()));
        countLines.flush();
        return InventoryCountResponse.from(inventoryCount);
    }

    @Transactional
    public InventoryCountResponse submit(UUID id, String responsibleUser) {
        InventoryCount inventoryCount = requireForUpdate(id);
        if (inventoryCount.getStatus() != InventoryCountStatus.OPEN) {
            throw new ConflictException(
                    "Only an open inventory count can be submitted");
        }
        ensureCountIsBounded(id);
        List<InventoryCountLine> lines = countLines
                .findByCountIdForUpdate(id);
        if (lines.isEmpty() || lines.stream().anyMatch(
                line -> line.getCountedQuantity() == null)) {
            throw new ConflictException(
                    "Every inventory count line must have a counted quantity");
        }
        inventoryCount.submit(requireActor(responsibleUser));
        counts.flush();
        return InventoryCountResponse.from(inventoryCount);
    }

    @Transactional
    public InventoryCountResponse post(UUID id, String responsibleUser) {
        InventoryCount inventoryCount = requireForUpdate(id);
        if (inventoryCount.getStatus() == InventoryCountStatus.POSTED) {
            return InventoryCountResponse.from(inventoryCount);
        }
        if (inventoryCount.getStatus() != InventoryCountStatus.SUBMITTED) {
            throw new ConflictException(
                    "Only a submitted inventory count can be posted");
        }
        ensureCountIsBounded(id);
        List<InventoryCountLine> lines = countLines
                .findByCountIdForUpdate(id);
        warehouses.lockActiveWarehouse(inventoryCount.getWarehouseId());
        String actor = requireActor(responsibleUser);
        for (InventoryCountLine line : lines) {
            if (line.getVariance() == null) {
                throw new ConflictException(
                        "Every inventory count line must have a variance");
            }
            inventory.postPhysicalCountAdjustment(
                    inventoryCount.getWarehouseId(), line.getProductId(),
                    line.getVariance(), inventoryCount.getId(), actor);
        }
        inventoryCount.post(actor);
        counts.flush();
        return InventoryCountResponse.from(inventoryCount);
    }

    @Transactional
    public InventoryCountResponse cancel(UUID id, String responsibleUser) {
        InventoryCount inventoryCount = requireForUpdate(id);
        if (inventoryCount.getStatus() == InventoryCountStatus.CANCELLED) {
            return InventoryCountResponse.from(inventoryCount);
        }
        if (inventoryCount.getStatus() == InventoryCountStatus.POSTED) {
            throw new ConflictException(
                    "A posted inventory count cannot be cancelled");
        }
        inventoryCount.cancel(requireActor(responsibleUser));
        counts.flush();
        return InventoryCountResponse.from(inventoryCount);
    }

    private List<UUID> resolveProductIds(
            CreateInventoryCountRequest request) {
        List<UUID> requestedIds = request.productIds() == null
                ? List.of() : request.productIds();
        if (request.scope() == InventoryCountScope.FULL) {
            if (!requestedIds.isEmpty()) {
                throw new BadRequestException(
                        "A full inventory count must not specify products");
            }
            List<UUID> allProductIds = warehouses
                    .productIdsForPhysicalCount(request.warehouseId(),
                            MAX_PRODUCTS_PER_COUNT + 1);
            if (allProductIds.isEmpty()) {
                throw new BadRequestException(
                        "The warehouse has no products to count");
            }
            if (allProductIds.size() > MAX_PRODUCTS_PER_COUNT) {
                throw new ConflictException(
                        "A full inventory count cannot contain more than 1000 products; use selected inventory counts");
            }
            return List.copyOf(allProductIds);
        }
        if (requestedIds.isEmpty()) {
            throw new BadRequestException(
                    "A selected inventory count requires at least one product");
        }
        if (requestedIds.size() > MAX_PRODUCTS_PER_COUNT) {
            throw new BadRequestException(
                    "An inventory count cannot contain more than 1000 selected products");
        }
        Set<UUID> uniqueIds = new HashSet<>();
        for (UUID productId : requestedIds) {
            if (productId == null) {
                throw new BadRequestException(
                        "Every selected inventory count product is required");
            }
            if (!uniqueIds.add(productId)) {
                throw new BadRequestException(
                        "An inventory count must not contain duplicate products");
            }
            products.requireProduct(productId);
            warehouses.requireActiveProduct(request.warehouseId(), productId);
        }
        return uniqueIds.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private Specification<InventoryCount> filters(
            InventoryCountStatus status, InventoryCountScope scope,
            UUID warehouseId, String folio) {
        String normalizedFolio = folio == null || folio.isBlank()
                ? null : folio.trim().toLowerCase(Locale.ROOT);
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (scope != null) {
                predicates.add(builder.equal(root.get("scope"), scope));
            }
            if (warehouseId != null) {
                predicates.add(builder.equal(
                        root.get("warehouseId"), warehouseId));
            }
            if (normalizedFolio != null) {
                predicates.add(builder.like(builder.lower(root.get("folio")),
                        "%" + normalizedFolio + "%"));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private InventoryCount requireForUpdate(UUID id) {
        return counts.findByIdForUpdate(id)
                .orElseThrow(() -> notFound(id));
    }

    private void ensureCountIsBounded(UUID id) {
        if (countLines.countByCountId(id) > MAX_PRODUCTS_PER_COUNT) {
            throw new ConflictException(
                    "An inventory count cannot contain more than 1000 products");
        }
    }

    private String requireActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException(
                    "The responsible user is required");
        }
        return actor.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private NotFoundException notFound(UUID id) {
        return new NotFoundException(
                "Inventory count %s was not found".formatted(id));
    }
}
