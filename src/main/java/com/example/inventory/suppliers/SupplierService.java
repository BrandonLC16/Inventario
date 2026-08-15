package com.example.inventory.suppliers;

import com.example.inventory.products.ProductCatalog;
import com.example.inventory.shared.BadRequestException;
import com.example.inventory.shared.ConflictException;
import com.example.inventory.shared.NotFoundException;
import com.example.inventory.shared.PageResponse;
import com.example.inventory.shared.PageSupport;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_MANAGER')")
@Transactional(readOnly = true)
public class SupplierService implements SupplierDirectory {

    private final SupplierRepository suppliers;
    private final SupplierProductRepository supplierProducts;
    private final ProductCatalog products;

    SupplierService(SupplierRepository suppliers,
                    SupplierProductRepository supplierProducts,
                    ProductCatalog products) {
        this.suppliers = suppliers;
        this.supplierProducts = supplierProducts;
        this.products = products;
    }

    public PageResponse<SupplierResponse> findAll(
            int page, int size, String code, String name,
            String fiscalIdentifier, Boolean active) {
        var pageable = PageSupport.request(page, size,
                Sort.by(Sort.Direction.ASC, "legalName")
                        .and(Sort.by(Sort.Direction.ASC, "id")));
        return PageResponse.from(
                suppliers.findAll(filters(code, name, fiscalIdentifier, active), pageable),
                SupplierResponse::from);
    }

    public SupplierResponse findById(UUID id) {
        return SupplierResponse.from(requireSupplier(id));
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        NormalizedSupplier normalized = normalize(request);
        ensureUnique(normalized, null);
        Supplier supplier = new Supplier(
                normalized.code(), normalized.legalName(), normalized.commercialName(),
                normalized.fiscalIdentifier(), normalized.email(), normalized.phone(),
                request.active());
        return SupplierResponse.from(suppliers.saveAndFlush(supplier));
    }

    @Transactional
    public SupplierResponse update(UUID id, SupplierRequest request) {
        Supplier supplier = requireSupplierForUpdate(id);
        NormalizedSupplier normalized = normalize(request);
        ensureUnique(normalized, id);
        boolean deactivating = supplier.isActive() && !request.active();
        supplier.update(
                normalized.code(), normalized.legalName(), normalized.commercialName(),
                normalized.fiscalIdentifier(), normalized.email(), normalized.phone(),
                request.active());
        if (deactivating) {
            supplierProducts.clearPreferredBySupplierId(id);
        }
        suppliers.flush();
        return SupplierResponse.from(supplier);
    }

    @Transactional
    public void deactivate(UUID id) {
        Supplier supplier = requireSupplierForUpdate(id);
        if (!supplier.isActive()) {
            return;
        }
        supplier.deactivate();
        supplierProducts.clearPreferredBySupplierId(id);
        suppliers.flush();
    }

    public PageResponse<SupplierProductResponse> findProducts(
            UUID supplierId, int page, int size) {
        requireSupplier(supplierId);
        var pageable = PageSupport.request(page, size,
                Sort.by(Sort.Direction.ASC, "productId"));
        return PageResponse.from(
                supplierProducts.findBySupplierId(supplierId, pageable),
                SupplierProductResponse::from);
    }

    @Transactional
    public SupplierProductResponse putProduct(
            UUID supplierId, UUID productId, SupplierProductRequest request) {
        if (request.preferred() && !request.active()) {
            throw new BadRequestException("A preferred supplier product must be active");
        }
        requireSupplierForUpdate(supplierId);
        products.requireProduct(productId);
        if (request.preferred()
                && supplierProducts.existsByProductIdAndPreferredTrueAndSupplierIdNot(
                        productId, supplierId)) {
            throw new ConflictException(
                    "Product %s already has a preferred supplier".formatted(productId));
        }
        SupplierProductId id = new SupplierProductId(supplierId, productId);
        SupplierProduct supplierProduct = supplierProducts.findById(id)
                .orElseGet(() -> new SupplierProduct(supplierId, productId));
        supplierProduct.update(
                trimToNull(request.supplierSku()), request.leadTimeDays(),
                request.minimumOrderQuantity(), request.lastUnitCost(),
                request.preferred(), request.active());
        return SupplierProductResponse.from(
                supplierProducts.saveAndFlush(supplierProduct));
    }

    @Transactional
    public void deactivateProduct(UUID supplierId, UUID productId) {
        requireSupplier(supplierId);
        SupplierProduct supplierProduct = supplierProducts.findById(
                        new SupplierProductId(supplierId, productId))
                .orElseThrow(() -> new NotFoundException(
                        "Product %s is not associated with supplier %s"
                                .formatted(productId, supplierId)));
        supplierProduct.deactivate();
        supplierProducts.flush();
    }

    @Override
    public void requireActiveSupplier(UUID supplierId) {
        Supplier supplier = requireSupplier(supplierId);
        if (!supplier.isActive()) {
            throw new ConflictException(
                    "Supplier %s is inactive".formatted(supplierId));
        }
    }

    @Override
    @Transactional
    public void updateLastUnitCostIfAssociated(UUID supplierId, UUID productId,
                                               java.math.BigDecimal unitCost) {
        supplierProducts.findByIdForUpdate(supplierId, productId)
                .ifPresent(product -> product.updateLastUnitCost(unitCost));
    }

    private Supplier requireSupplier(UUID id) {
        return suppliers.findById(id)
                .orElseThrow(() -> supplierNotFound(id));
    }

    private Supplier requireSupplierForUpdate(UUID id) {
        return suppliers.findByIdForUpdate(id)
                .orElseThrow(() -> supplierNotFound(id));
    }

    private void ensureUnique(NormalizedSupplier supplier, UUID currentId) {
        boolean duplicateCode = currentId == null
                ? suppliers.existsByCodeIgnoreCase(supplier.code())
                : suppliers.existsByCodeIgnoreCaseAndIdNot(supplier.code(), currentId);
        if (duplicateCode) {
            throw new ConflictException(
                    "Supplier code %s already exists".formatted(supplier.code()));
        }
        boolean duplicateFiscalIdentifier = supplier.fiscalIdentifier() != null
                && (currentId == null
                ? suppliers.existsByFiscalIdentifier(supplier.fiscalIdentifier())
                : suppliers.existsByFiscalIdentifierAndIdNot(
                        supplier.fiscalIdentifier(), currentId));
        if (duplicateFiscalIdentifier) {
            throw new ConflictException("Supplier fiscal identifier already exists");
        }
        boolean duplicateEmail = supplier.email() != null
                && (currentId == null
                ? suppliers.existsByEmailIgnoreCase(supplier.email())
                : suppliers.existsByEmailIgnoreCaseAndIdNot(
                        supplier.email(), currentId));
        if (duplicateEmail) {
            throw new ConflictException("Supplier email already exists");
        }
    }

    private Specification<Supplier> filters(
            String code, String name, String fiscalIdentifier, Boolean active) {
        String normalizedCode = trimToNull(code);
        String normalizedName = trimToNull(name);
        String normalizedFiscalIdentifier = trimToNull(fiscalIdentifier);
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (normalizedCode != null) {
                predicates.add(builder.like(builder.lower(root.get("code")),
                        containsPattern(normalizedCode)));
            }
            if (normalizedName != null) {
                String pattern = containsPattern(normalizedName);
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("legalName")), pattern),
                        builder.like(builder.lower(root.get("commercialName")), pattern)));
            }
            if (normalizedFiscalIdentifier != null) {
                predicates.add(builder.like(
                        builder.lower(root.get("fiscalIdentifier")),
                        containsPattern(normalizedFiscalIdentifier)));
            }
            if (active != null) {
                predicates.add(builder.equal(root.get("active"), active));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private NormalizedSupplier normalize(SupplierRequest request) {
        return new NormalizedSupplier(
                request.code().trim().toUpperCase(Locale.ROOT),
                request.legalName().trim(),
                trimToNull(request.commercialName()),
                upperToNull(request.fiscalIdentifier()),
                lowerToNull(request.email()),
                trimToNull(request.phone()));
    }

    private String containsPattern(String value) {
        return "%" + value.toLowerCase(Locale.ROOT) + "%";
    }

    private String upperToNull(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String lowerToNull(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private NotFoundException supplierNotFound(UUID id) {
        return new NotFoundException("Supplier %s was not found".formatted(id));
    }

    private record NormalizedSupplier(
            String code,
            String legalName,
            String commercialName,
            String fiscalIdentifier,
            String email,
            String phone) {
    }
}
