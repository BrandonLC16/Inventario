package com.example.inventory.suppliers;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@IdClass(SupplierProductId.class)
@Table(name = "supplier_products")
class SupplierProduct {

    @Id
    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Id
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "supplier_sku", length = 64)
    private String supplierSku;

    @Column(name = "lead_time_days", nullable = false)
    private int leadTimeDays;

    @Column(name = "minimum_order_quantity", nullable = false)
    private int minimumOrderQuantity;

    @Column(name = "last_unit_cost", precision = 14, scale = 4)
    private BigDecimal lastUnitCost;

    @Column(nullable = false)
    private boolean preferred;

    @Column(nullable = false)
    private boolean active;

    protected SupplierProduct() {
    }

    SupplierProduct(UUID supplierId, UUID productId) {
        this.supplierId = supplierId;
        this.productId = productId;
    }

    void update(String supplierSku, int leadTimeDays, int minimumOrderQuantity,
                BigDecimal lastUnitCost, boolean preferred, boolean active) {
        this.supplierSku = supplierSku;
        this.leadTimeDays = leadTimeDays;
        this.minimumOrderQuantity = minimumOrderQuantity;
        this.lastUnitCost = lastUnitCost;
        this.preferred = preferred;
        this.active = active;
    }

    void deactivate() {
        active = false;
        preferred = false;
    }

    UUID getSupplierId() { return supplierId; }
    UUID getProductId() { return productId; }
    String getSupplierSku() { return supplierSku; }
    int getLeadTimeDays() { return leadTimeDays; }
    int getMinimumOrderQuantity() { return minimumOrderQuantity; }
    BigDecimal getLastUnitCost() { return lastUnitCost; }
    boolean isPreferred() { return preferred; }
    boolean isActive() { return active; }
}
