package com.example.inventory.suppliers;

import com.example.inventory.products.ProductCatalog;
import com.example.inventory.shared.BadRequestException;
import com.example.inventory.shared.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierServiceTest {

    @Mock
    private SupplierRepository suppliers;

    @Mock
    private SupplierProductRepository supplierProducts;

    @Mock
    private ProductCatalog products;

    @Test
    void requireActiveSupplierRejectsInactiveSupplierForNewPurchases() {
        Supplier supplier = supplier(false);
        when(suppliers.findById(supplier.getId())).thenReturn(Optional.of(supplier));

        ConflictException error = assertThrows(ConflictException.class,
                () -> service().requireActiveSupplier(supplier.getId()));

        assertEquals("Supplier %s is inactive".formatted(supplier.getId()),
                error.getMessage());
    }

    @Test
    void deactivationIsLogicalAndClearsPreferredRelationships() {
        Supplier supplier = supplier(true);
        when(suppliers.findByIdForUpdate(supplier.getId()))
                .thenReturn(Optional.of(supplier));

        service().deactivate(supplier.getId());

        verify(supplierProducts).clearPreferredBySupplierId(supplier.getId());
        verify(suppliers).flush();
        assertFalse(supplier.isActive());
    }

    @Test
    void preferredAssociationMustBeActive() {
        UUID supplierId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        SupplierProductRequest request = new SupplierProductRequest(
                "SKU", 1, 1, null, true, false);

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service().putProduct(supplierId, productId, request));

        assertEquals("A preferred supplier product must be active", error.getMessage());
    }

    private SupplierService service() {
        return new SupplierService(suppliers, supplierProducts, products);
    }

    private Supplier supplier(boolean active) {
        return new Supplier("SUP-1", "Supplier", null,
                null, null, null, active);
    }
}
