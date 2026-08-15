package com.example.inventory.transfers;

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

import java.time.Instant;
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
public class InventoryTransferService {

    private final InventoryTransferRepository transfers;
    private final InventoryTransferItemRepository transferItems;
    private final ProductCatalog products;
    private final WarehouseDirectory warehouses;
    private final InventoryOperations inventory;

    public InventoryTransferService(
            InventoryTransferRepository transfers,
            InventoryTransferItemRepository transferItems,
            ProductCatalog products,
            WarehouseDirectory warehouses,
            InventoryOperations inventory) {
        this.transfers = transfers;
        this.transferItems = transferItems;
        this.products = products;
        this.warehouses = warehouses;
        this.inventory = inventory;
    }

    public PageResponse<InventoryTransferResponse> findAll(
            int page, int size, InventoryTransferStatus status,
            Instant from, Instant to, UUID sourceWarehouseId,
            UUID destinationWarehouseId, String folio) {
        PageSupport.validateDateRange(from, to);
        var pageable = PageSupport.request(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt")
                        .and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.from(transfers.findAll(filters(
                        status, from, to, sourceWarehouseId,
                        destinationWarehouseId, folio), pageable),
                InventoryTransferResponse::from);
    }

    public InventoryTransferResponse findById(UUID id) {
        return InventoryTransferResponse.from(transfers.findDetailedById(id)
                .orElseThrow(() -> notFound(id)));
    }

    @Transactional
    public InventoryTransferResponse create(
            CreateInventoryTransferRequest request, String responsibleUser) {
        if (request == null) {
            throw new BadRequestException("An inventory transfer request is required");
        }
        validateAndLockWarehouses(
                request.sourceWarehouseId(), request.destinationWarehouseId());
        List<InventoryTransferLine> lines = validateAndMapItems(request.items());
        String folio = "TRF-%010d".formatted(transfers.nextFolioSequence());
        InventoryTransfer transfer = new InventoryTransfer(
                folio, request.sourceWarehouseId(),
                request.destinationWarehouseId(),
                requireActor(responsibleUser), lines);
        return InventoryTransferResponse.from(transfers.saveAndFlush(transfer));
    }

    @Transactional
    public InventoryTransferResponse replaceItems(
            UUID id, UpdateInventoryTransferItemsRequest request) {
        InventoryTransfer transfer = requireForUpdate(id);
        if (transfer.getStatus() != InventoryTransferStatus.DRAFT) {
            throw new ConflictException(
                    "Only a draft inventory transfer can be edited");
        }
        if (request == null) {
            throw new BadRequestException("Inventory transfer items are required");
        }
        UUID sourceId = request.sourceWarehouseId() == null
                ? transfer.getSourceWarehouseId() : request.sourceWarehouseId();
        UUID destinationId = request.destinationWarehouseId() == null
                ? transfer.getDestinationWarehouseId()
                : request.destinationWarehouseId();
        validateAndLockWarehouses(sourceId, destinationId);
        List<InventoryTransferLine> lines = validateAndMapItems(request.items());
        transfer.changeWarehouses(sourceId, destinationId);
        transfer.replaceItems(lines);
        transfers.flush();
        return InventoryTransferResponse.from(transfer);
    }

    @Transactional
    public InventoryTransferResponse dispatch(UUID id, String responsibleUser) {
        InventoryTransfer transfer = requireForUpdate(id);
        List<InventoryTransferItem> items = transferItems
                .findByTransferIdForUpdate(id);
        if (transfer.getStatus() == InventoryTransferStatus.IN_TRANSIT
                || transfer.getStatus() == InventoryTransferStatus.RECEIVED) {
            return InventoryTransferResponse.from(transfer);
        }
        if (transfer.getStatus() != InventoryTransferStatus.DRAFT) {
            throw new ConflictException(
                    "Only a draft inventory transfer can be dispatched");
        }
        if (items.isEmpty()) {
            throw new ConflictException(
                    "An inventory transfer must contain items before dispatch");
        }
        validateAndLockWarehouses(
                transfer.getSourceWarehouseId(),
                transfer.getDestinationWarehouseId());
        String actor = requireActor(responsibleUser);
        items.forEach(item -> inventory.transferOut(
                transfer.getSourceWarehouseId(), item.getProductId(),
                item.getQuantity(), transfer.getId(), actor));
        transfer.dispatch(actor);
        transfers.flush();
        return InventoryTransferResponse.from(transfer);
    }

    @Transactional
    public InventoryTransferResponse receive(UUID id, String responsibleUser) {
        InventoryTransfer transfer = requireForUpdate(id);
        List<InventoryTransferItem> items = transferItems
                .findByTransferIdForUpdate(id);
        if (transfer.getStatus() == InventoryTransferStatus.RECEIVED) {
            return InventoryTransferResponse.from(transfer);
        }
        if (transfer.getStatus() != InventoryTransferStatus.IN_TRANSIT) {
            throw new ConflictException(
                    "Only an in-transit inventory transfer can be received");
        }
        validateAndLockWarehouses(
                transfer.getSourceWarehouseId(),
                transfer.getDestinationWarehouseId());
        String actor = requireActor(responsibleUser);
        items.forEach(item -> inventory.transferIn(
                transfer.getDestinationWarehouseId(), item.getProductId(),
                item.getQuantity(), transfer.getId(), actor));
        transfer.receive(actor);
        transfers.flush();
        return InventoryTransferResponse.from(transfer);
    }

    @Transactional
    public InventoryTransferResponse cancel(UUID id, String responsibleUser) {
        InventoryTransfer transfer = requireForUpdate(id);
        if (transfer.getStatus() != InventoryTransferStatus.DRAFT) {
            throw new ConflictException(
                    "Only a draft inventory transfer can be cancelled");
        }
        transfer.cancel(requireActor(responsibleUser));
        transfers.flush();
        return InventoryTransferResponse.from(transfer);
    }

    private List<InventoryTransferLine> validateAndMapItems(
            List<CreateInventoryTransferItemRequest> requestedItems) {
        if (requestedItems == null || requestedItems.isEmpty()) {
            throw new BadRequestException(
                    "An inventory transfer must contain at least one item");
        }
        if (requestedItems.size() > 100) {
            throw new BadRequestException(
                    "An inventory transfer cannot contain more than 100 items");
        }
        Set<UUID> productIds = new HashSet<>();
        List<InventoryTransferLine> lines = new ArrayList<>();
        for (CreateInventoryTransferItemRequest item : requestedItems) {
            if (item == null || item.productId() == null || item.quantity() <= 0) {
                throw new BadRequestException(
                        "Every inventory transfer item requires a product and a positive quantity");
            }
            if (!productIds.add(item.productId())) {
                throw new BadRequestException(
                        "An inventory transfer must not contain duplicate products");
            }
            products.requireProduct(item.productId());
            lines.add(new InventoryTransferLine(item.productId(), item.quantity()));
        }
        return lines.stream().sorted(Comparator.comparing(
                InventoryTransferLine::productId)).toList();
    }

    private void validateAndLockWarehouses(UUID sourceId, UUID destinationId) {
        if (sourceId == null || destinationId == null) {
            throw new BadRequestException(
                    "Source and destination warehouses are required");
        }
        if (sourceId.equals(destinationId)) {
            throw new BadRequestException(
                    "Source and destination warehouses must be different");
        }
        if (sourceId.compareTo(destinationId) < 0) {
            warehouses.lockActiveWarehouse(sourceId);
            warehouses.lockActiveWarehouse(destinationId);
        } else {
            warehouses.lockActiveWarehouse(destinationId);
            warehouses.lockActiveWarehouse(sourceId);
        }
    }

    private Specification<InventoryTransfer> filters(
            InventoryTransferStatus status, Instant from, Instant to,
            UUID sourceWarehouseId, UUID destinationWarehouseId, String folio) {
        String normalizedFolio = folio == null || folio.isBlank()
                ? null : folio.trim().toLowerCase(Locale.ROOT);
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(
                        root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(
                        root.get("createdAt"), to));
            }
            if (sourceWarehouseId != null) {
                predicates.add(builder.equal(root.get("sourceWarehouseId"),
                        sourceWarehouseId));
            }
            if (destinationWarehouseId != null) {
                predicates.add(builder.equal(
                        root.get("destinationWarehouseId"),
                        destinationWarehouseId));
            }
            if (normalizedFolio != null) {
                predicates.add(builder.like(builder.lower(root.get("folio")),
                        "%" + normalizedFolio + "%"));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private InventoryTransfer requireForUpdate(UUID id) {
        return transfers.findByIdForUpdate(id)
                .orElseThrow(() -> notFound(id));
    }

    private String requireActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("The responsible user is required");
        }
        return actor.trim();
    }

    private NotFoundException notFound(UUID id) {
        return new NotFoundException(
                "Inventory transfer %s was not found".formatted(id));
    }
}
