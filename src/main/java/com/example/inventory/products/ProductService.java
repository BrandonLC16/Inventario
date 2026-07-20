package com.example.inventory.products;

import com.example.inventory.shared.ConflictException;
import com.example.inventory.shared.NotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class ProductService implements ProductCatalog {

    private final ProductRepository repository;

    ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    List<ProductResponse> findAll() {
        return repository.findAll(Sort.by("name").ascending()).stream()
                .map(ProductResponse::from)
                .toList();
    }

    ProductResponse findById(UUID id) {
        return ProductResponse.from(findEntity(id));
    }

    @Transactional
    ProductResponse create(ProductRequest request) {
        String sku = normalizeSku(request.sku());
        ensureSkuIsAvailable(sku, null);
        Product product = new Product(sku, request.name().trim(), trimToNull(request.description()),
                request.price(), request.active());
        return ProductResponse.from(repository.save(product));
    }

    @Transactional
    ProductResponse update(UUID id, ProductRequest request) {
        Product product = findEntity(id);
        String sku = normalizeSku(request.sku());
        ensureSkuIsAvailable(sku, id);
        product.update(sku, request.name().trim(), trimToNull(request.description()),
                request.price(), request.active());
        return ProductResponse.from(product);
    }

    @Transactional
    void delete(UUID id) {
        repository.delete(findEntity(id));
    }

    @Override
    public void requireProduct(UUID productId) {
        if (!repository.existsById(productId)) {
            throw new NotFoundException("Product %s was not found".formatted(productId));
        }
    }

    private Product findEntity(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product %s was not found".formatted(id)));
    }

    private void ensureSkuIsAvailable(String sku, UUID currentId) {
        boolean exists = currentId == null
                ? repository.existsBySkuIgnoreCase(sku)
                : repository.existsBySkuIgnoreCaseAndIdNot(sku, currentId);
        if (exists) {
            throw new ConflictException("SKU %s already exists".formatted(sku));
        }
    }

    private String normalizeSku(String sku) {
        return sku.trim().toUpperCase();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
