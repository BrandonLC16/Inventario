-- Multi-warehouse inventory. Existing rows are assigned to deterministic MAIN.
CREATE TABLE warehouses (
    id UUID PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_warehouses_code UNIQUE (code),
    CONSTRAINT ck_warehouses_code_normalized
        CHECK (code = upper(btrim(code)) AND length(btrim(code)) > 0),
    CONSTRAINT ck_warehouses_name_not_blank CHECK (length(btrim(name)) > 0)
);

INSERT INTO warehouses (
    id, code, name, description, active, created_at, updated_at
) VALUES (
    '00000000-0000-0000-0000-000000000001',
    'MAIN',
    'Main warehouse',
    'Warehouse created by the V10 multi-warehouse migration',
    TRUE,
    TIMESTAMPTZ '2000-01-01 00:00:00+00',
    TIMESTAMPTZ '2000-01-01 00:00:00+00'
);

CREATE TABLE warehouse_product_settings (
    warehouse_id UUID NOT NULL,
    product_id UUID NOT NULL,
    minimum_stock INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_warehouse_product_settings
        PRIMARY KEY (warehouse_id, product_id),
    CONSTRAINT fk_warehouse_product_settings_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouses (id) ON DELETE CASCADE,
    CONSTRAINT fk_warehouse_product_settings_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT ck_warehouse_product_settings_minimum_stock
        CHECK (minimum_stock >= 0)
);

INSERT INTO warehouse_product_settings (
    warehouse_id, product_id, minimum_stock, active
)
SELECT '00000000-0000-0000-0000-000000000001',
       product.id,
       product.minimum_stock,
       TRUE
FROM products product;

ALTER TABLE inventory
    ADD COLUMN warehouse_id UUID;

UPDATE inventory
SET warehouse_id = '00000000-0000-0000-0000-000000000001';

ALTER TABLE inventory
    DROP CONSTRAINT inventory_pkey,
    ALTER COLUMN warehouse_id SET NOT NULL,
    ADD CONSTRAINT pk_inventory PRIMARY KEY (warehouse_id, product_id),
    ADD CONSTRAINT fk_inventory_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouses (id);

ALTER TABLE inventory_reservations
    ADD COLUMN warehouse_id UUID;

UPDATE inventory_reservations
SET warehouse_id = '00000000-0000-0000-0000-000000000001';

ALTER TABLE inventory_reservations
    DROP CONSTRAINT uk_inventory_reservations_order_product,
    ALTER COLUMN warehouse_id SET NOT NULL,
    ADD CONSTRAINT fk_inventory_reservations_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    ADD CONSTRAINT uk_inventory_reservations_order_warehouse_product
        UNIQUE (order_id, warehouse_id, product_id);

DROP INDEX idx_inventory_reservations_product;
CREATE INDEX idx_inventory_reservations_warehouse_product
    ON inventory_reservations (warehouse_id, product_id);

ALTER TABLE stock_movements
    ADD COLUMN warehouse_id UUID;

UPDATE stock_movements
SET warehouse_id = '00000000-0000-0000-0000-000000000001';

ALTER TABLE stock_movements
    ALTER COLUMN warehouse_id SET NOT NULL,
    ADD CONSTRAINT fk_stock_movements_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouses (id);

DROP INDEX idx_stock_movements_product_occurred;
DROP INDEX uk_stock_movements_final_order_event;

CREATE INDEX idx_stock_movements_warehouse_product_occurred
    ON stock_movements (warehouse_id, product_id, occurred_at DESC, id DESC);
CREATE UNIQUE INDEX uk_stock_movements_final_order_event
    ON stock_movements (
        warehouse_id, product_id, movement_type, business_reference
    )
    WHERE movement_type IN ('ORDER_CONFIRMED', 'ORDER_CANCELLED');

ALTER TABLE orders
    ADD COLUMN fulfillment_warehouse_id UUID;

UPDATE orders
SET fulfillment_warehouse_id = '00000000-0000-0000-0000-000000000001';

ALTER TABLE orders
    ALTER COLUMN fulfillment_warehouse_id SET NOT NULL,
    ADD CONSTRAINT fk_orders_fulfillment_warehouse
        FOREIGN KEY (fulfillment_warehouse_id) REFERENCES warehouses (id);

CREATE INDEX idx_orders_fulfillment_warehouse_status
    ON orders (fulfillment_warehouse_id, status);

DROP INDEX idx_products_replenishment;
ALTER TABLE products
    DROP CONSTRAINT ck_products_minimum_stock_non_negative,
    DROP COLUMN minimum_stock;
