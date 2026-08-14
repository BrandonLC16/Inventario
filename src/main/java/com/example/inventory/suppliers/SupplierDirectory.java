package com.example.inventory.suppliers;

import java.util.UUID;

/** Public contract exposed to modules that create purchases or receipts. */
public interface SupplierDirectory {

    /** Rejects missing and inactive suppliers for new purchasing operations. */
    void requireActiveSupplier(UUID supplierId);
}
