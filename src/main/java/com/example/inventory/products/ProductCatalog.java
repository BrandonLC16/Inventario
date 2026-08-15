package com.example.inventory.products;

import java.util.UUID;

/** Public contract exposed by the products module to other business modules. */
public interface ProductCatalog {

    /** Locks and requires an active, non-deleted product for an operational change. */
    void requireProduct(UUID productId);

    /** Locks an active product and captures the values needed by a new business document. */
    ProductSnapshot requireProductSnapshot(UUID productId);

    /** Requires a non-deleted product for read-only operational views, even when inactive. */
    void requireVisibleProduct(UUID productId);

    /** Requires the database record even when it was soft-deleted. */
    void requireStoredProduct(UUID productId);

    /** Locks the database record for a compensating operation on existing business state. */
    void lockStoredProduct(UUID productId);
}
