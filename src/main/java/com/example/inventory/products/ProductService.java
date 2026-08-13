package com.example.inventory.products;

import com.example.inventory.shared.ConflictException;
import com.example.inventory.shared.NotFoundException;
import com.example.inventory.shared.PageResponse;
import com.example.inventory.shared.PageSupport;
import com.example.inventory.warehouses.WarehouseDirectory;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class ProductService implements ProductCatalog {

    private final ProductRepository repository;
    private final WarehouseDirectory warehouses;

    ProductService(ProductRepository repository, WarehouseDirectory warehouses) {
        this.repository = repository;
        this.warehouses = warehouses;
    }

    PageResponse<ProductResponse> findAll(int page, int size, String sku,
                                          String name, Boolean active) {
        var pageable = PageSupport.request(page, size,
                Sort.by(Sort.Direction.ASC, "name")
                        .and(Sort.by(Sort.Direction.ASC, "id")));
        return PageResponse.from(repository.findAll(filters(sku, name, active), pageable),
                ProductResponse::from);
    }

    ProductResponse findById(UUID id) {
        return ProductResponse.from(findEntity(id));
    }

    @Transactional
    ProductResponse create(ProductRequest request) {
        String sku = normalizeSku(request.sku());
        ensureSkuIsAvailable(sku, null);
        Product product = repository.saveAndFlush(new Product(sku, request.name().trim(),
                trimToNull(request.description()), request.price(), request.active()));
        warehouses.registerProduct(product.getId());
        warehouses.configureProduct(WarehouseDirectory.MAIN_WAREHOUSE_ID,
                product.getId(), minimumStock(request.minimumStock()), true);
        return ProductResponse.from(product);
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
        findEntity(id).markDeleted();
    }

    @Override
    public void requireProduct(UUID productId) {
        if (!repository.existsByIdAndDeletedFalse(productId)) {
            throw new NotFoundException("Product %s was not found".formatted(productId));
        }
    }

    @Override
    public ProductSnapshot requireProductSnapshot(UUID productId) {
        Product product = findEntity(productId);
        return new ProductSnapshot(product.getId(), product.getPrice());
    }

    @Override
    public void requireStoredProduct(UUID productId) {
        if (!repository.existsById(productId)) {
            throw new NotFoundException("Product %s was not found".formatted(productId));
        }
    }

    private Product findEntity(UUID id) {
        return repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Product %s was not found".formatted(id)));
    }

    private Specification<Product> filters(String sku, String name, Boolean active) {
        String normalizedSku = trimToNull(sku);
        String normalizedName = trimToNull(name);
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isFalse(root.get("deleted")));
            if (normalizedSku != null) {
                predicates.add(builder.like(builder.lower(root.get("sku")),
                        "%" + normalizedSku.toLowerCase(java.util.Locale.ROOT) + "%"));
            }
            if (normalizedName != null) {
                predicates.add(builder.like(builder.lower(root.get("name")),
                        "%" + normalizedName.toLowerCase(java.util.Locale.ROOT) + "%"));
            }
            if (active != null) {
                predicates.add(builder.equal(root.get("active"), active));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private int minimumStock(Integer value) {
        return value == null ? 0 : value;
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
