package com.example.inventory.inventory;

import com.example.inventory.products.ProductCatalog;
import com.example.inventory.shared.BadRequestException;
import com.example.inventory.shared.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import com.example.inventory.shared.PageResponse;
import com.example.inventory.shared.PageSupport;
import org.springframework.data.domain.Sort;

@Service
@Transactional(readOnly = true)
class InventoryService implements InventoryOperations {

    private final InventoryRepository repository;
    private final StockMovementRepository movementRepository;
    private final ProductCatalog productCatalog;

    InventoryService(InventoryRepository repository, StockMovementRepository movementRepository,
                     ProductCatalog productCatalog) {
        this.repository = repository;
        this.movementRepository = movementRepository;
        this.productCatalog = productCatalog;
    }

    InventoryResponse findByProductId(UUID productId) {
        productCatalog.requireProduct(productId);
        return repository.findById(productId)
                .map(InventoryResponse::from)
                .orElseGet(() -> InventoryResponse.empty(productId));
    }

    @Transactional
    InventoryResponse adjust(UUID productId, int delta, String responsibleUser) {
        return adjust(productId, delta, null, responsibleUser);
    }

    @Transactional
    InventoryResponse adjust(UUID productId, int delta, String reference,
                             String responsibleUser) {
        String actor = requireResponsibleUser(responsibleUser);
        productCatalog.requireProduct(productId);
        if (delta == 0) {
            throw new BadRequestException("Inventory adjustment must not be zero");
        }
        boolean initialStock = repository.ensureExists(productId) == 1;
        InventoryItem item = lockInventory(productId);
        int balanceBefore = item.getQuantity();
        try {
            item.changeQuantity(delta);
        } catch (IllegalArgumentException exception) {
            if (delta < 0) {
                throw insufficientStock(productId);
            }
            throw new BadRequestException(exception.getMessage());
        }
        StockMovementType type = initialStock && delta > 0
                ? StockMovementType.INITIAL_STOCK
                : delta > 0 ? StockMovementType.MANUAL_IN : StockMovementType.MANUAL_OUT;
        String businessReference = normalizeReference(reference);
        recordMovement(item, type, delta, balanceBefore,
                businessReference == null
                        ? "MANUAL:" + UUID.randomUUID()
                        : businessReference,
                actor);
        return InventoryResponse.from(item);
    }

    PageResponse<LowStockResponse> findLowStock(
            int page, int size, String search, boolean outOfStockOnly) {
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        var pageable = PageSupport.request(page, size, Sort.unsorted());
        return PageResponse.from(repository.findLowStock(
                normalizedSearch, outOfStockOnly, pageable), LowStockResponse::from);
    }

    @Override
    @Transactional
    public void consumeForOrder(UUID productId, int quantity, UUID orderId,
                                String responsibleUser) {
        String actor = requireResponsibleUser(responsibleUser);
        requirePositive(quantity);
        productCatalog.requireProduct(productId);
        repository.ensureExists(productId);
        InventoryItem item = lockInventory(productId);
        int balanceBefore = item.getQuantity();
        try {
            item.changeQuantity(-quantity);
        } catch (IllegalArgumentException exception) {
            throw insufficientStock(productId);
        }
        recordMovement(item, StockMovementType.ORDER_CONFIRMED, -quantity,
                balanceBefore, orderId.toString(), actor);
    }

    @Override
    @Transactional
    public void restoreForOrder(UUID productId, int quantity, UUID orderId,
                                String responsibleUser) {
        String actor = requireResponsibleUser(responsibleUser);
        requirePositive(quantity);
        productCatalog.requireStoredProduct(productId);
        repository.ensureExists(productId);
        InventoryItem item = lockInventory(productId);
        int balanceBefore = item.getQuantity();
        try {
            item.changeQuantity(quantity);
        } catch (IllegalArgumentException exception) {
            throw new ConflictException(exception.getMessage());
        }
        recordMovement(item, StockMovementType.ORDER_CANCELLED, quantity,
                balanceBefore, orderId.toString(), actor);
    }

    private InventoryItem lockInventory(UUID productId) {
        return repository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new ConflictException(
                        "Inventory for product %s could not be initialized".formatted(productId)));
    }

    private BadRequestException insufficientStock(UUID productId) {
        return new BadRequestException("Inventory quantity cannot be negative");
    }

    private void recordMovement(InventoryItem item, StockMovementType type, int delta,
                                int balanceBefore, String businessReference,
                                String responsibleUser) {
        movementRepository.save(new StockMovement(item.getProductId(), type, delta,
                balanceBefore, item.getQuantity(), businessReference, responsibleUser));
    }

    private void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new BadRequestException("Quantity must be greater than zero");
        }
    }

    private String requireResponsibleUser(String responsibleUser) {
        if (responsibleUser == null || responsibleUser.isBlank()) {
            throw new IllegalArgumentException("The responsible user is required");
        }
        return responsibleUser.trim();
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
