package com.example.inventory.inventory;

import com.example.inventory.products.ProductCatalog;
import com.example.inventory.shared.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
class InventoryService {

    private final InventoryRepository repository;
    private final ProductCatalog productCatalog;

    InventoryService(InventoryRepository repository, ProductCatalog productCatalog) {
        this.repository = repository;
        this.productCatalog = productCatalog;
    }

    InventoryResponse findByProductId(UUID productId) {
        productCatalog.requireProduct(productId);
        return repository.findById(productId)
                .map(InventoryResponse::from)
                .orElseGet(() -> InventoryResponse.empty(productId));
    }

    @Transactional
    InventoryResponse adjust(UUID productId, int delta) {
        productCatalog.requireProduct(productId);
        InventoryItem item = repository.findByProductIdForUpdate(productId)
                .orElseGet(() -> new InventoryItem(productId));
        try {
            item.changeQuantity(delta);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(exception.getMessage());
        }
        return InventoryResponse.from(repository.save(item));
    }
}
