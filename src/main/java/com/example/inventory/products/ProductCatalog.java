package com.example.inventory.products;

import java.util.UUID;

/** Public contract exposed by the products module to other business modules. */
public interface ProductCatalog {

    void requireProduct(UUID productId);
}
