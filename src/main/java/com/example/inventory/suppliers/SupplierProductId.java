package com.example.inventory.suppliers;

import java.io.Serializable;
import java.io.Serial;
import java.util.Objects;
import java.util.UUID;

final class SupplierProductId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private UUID supplierId;
    private UUID productId;

    SupplierProductId() {
    }

    SupplierProductId(UUID supplierId, UUID productId) {
        this.supplierId = supplierId;
        this.productId = productId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SupplierProductId that)) {
            return false;
        }
        return Objects.equals(supplierId, that.supplierId)
                && Objects.equals(productId, that.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(supplierId, productId);
    }
}
