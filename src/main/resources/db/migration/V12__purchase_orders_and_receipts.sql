CREATE SEQUENCE purchase_orders_folio_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE purchase_receipts_folio_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE purchase_orders (
    id UUID PRIMARY KEY,
    folio VARCHAR(32) NOT NULL,
    supplier_id UUID NOT NULL,
    destination_warehouse_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    total NUMERIC(20, 4) NOT NULL,
    supplier_reference VARCHAR(128),
    issued_at TIMESTAMPTZ,
    issued_by VARCHAR(255),
    cancelled_at TIMESTAMPTZ,
    cancelled_by VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_purchase_orders_folio UNIQUE (folio),
    CONSTRAINT fk_purchase_orders_supplier
        FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT fk_purchase_orders_destination_warehouse
        FOREIGN KEY (destination_warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT ck_purchase_orders_status CHECK (status IN (
        'DRAFT', 'ISSUED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED'
    )),
    CONSTRAINT ck_purchase_orders_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_purchase_orders_total_non_negative CHECK (total >= 0),
    CONSTRAINT ck_purchase_orders_supplier_reference_not_blank CHECK (
        supplier_reference IS NULL OR length(btrim(supplier_reference)) > 0
    ),
    CONSTRAINT ck_purchase_orders_lifecycle_audit CHECK (
        (status = 'DRAFT'
            AND issued_at IS NULL AND issued_by IS NULL
            AND cancelled_at IS NULL AND cancelled_by IS NULL)
        OR
        (status IN ('ISSUED', 'PARTIALLY_RECEIVED', 'RECEIVED')
            AND issued_at IS NOT NULL AND issued_by IS NOT NULL
            AND cancelled_at IS NULL AND cancelled_by IS NULL)
        OR
        (status = 'CANCELLED'
            AND ((issued_at IS NULL AND issued_by IS NULL)
                OR (issued_at IS NOT NULL AND issued_by IS NOT NULL))
            AND cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL)
    )
);

CREATE TABLE purchase_order_items (
    id UUID PRIMARY KEY,
    purchase_order_id UUID NOT NULL,
    product_id UUID NOT NULL,
    supplier_sku VARCHAR(64),
    ordered_quantity INTEGER NOT NULL,
    received_quantity INTEGER NOT NULL DEFAULT 0,
    unit_cost NUMERIC(14, 4) NOT NULL,
    subtotal NUMERIC(20, 4) NOT NULL,
    CONSTRAINT fk_purchase_order_items_order
        FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_purchase_order_items_product
        FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT uk_purchase_order_items_order_product
        UNIQUE (purchase_order_id, product_id),
    CONSTRAINT ck_purchase_order_items_supplier_sku_not_blank CHECK (
        supplier_sku IS NULL OR length(btrim(supplier_sku)) > 0
    ),
    CONSTRAINT ck_purchase_order_items_ordered_quantity_positive
        CHECK (ordered_quantity > 0),
    CONSTRAINT ck_purchase_order_items_received_quantity_valid
        CHECK (received_quantity >= 0 AND received_quantity <= ordered_quantity),
    CONSTRAINT ck_purchase_order_items_unit_cost_non_negative CHECK (unit_cost >= 0),
    CONSTRAINT ck_purchase_order_items_subtotal_consistent
        CHECK (subtotal = unit_cost * ordered_quantity)
);

CREATE TABLE purchase_receipts (
    id UUID PRIMARY KEY,
    folio VARCHAR(32) NOT NULL,
    purchase_order_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    external_reference VARCHAR(128) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    received_by VARCHAR(255) NOT NULL,
    CONSTRAINT uk_purchase_receipts_folio UNIQUE (folio),
    CONSTRAINT uk_purchase_receipts_order_external_reference
        UNIQUE (purchase_order_id, external_reference),
    CONSTRAINT fk_purchase_receipts_order
        FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id),
    CONSTRAINT fk_purchase_receipts_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT ck_purchase_receipts_external_reference_not_blank
        CHECK (length(btrim(external_reference)) > 0)
);

CREATE TABLE purchase_receipt_items (
    receipt_id UUID NOT NULL,
    purchase_order_item_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    unit_cost NUMERIC(14, 4) NOT NULL,
    CONSTRAINT pk_purchase_receipt_items
        PRIMARY KEY (receipt_id, purchase_order_item_id),
    CONSTRAINT fk_purchase_receipt_items_receipt
        FOREIGN KEY (receipt_id) REFERENCES purchase_receipts (id) ON DELETE CASCADE,
    CONSTRAINT fk_purchase_receipt_items_order_item
        FOREIGN KEY (purchase_order_item_id) REFERENCES purchase_order_items (id),
    CONSTRAINT ck_purchase_receipt_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_purchase_receipt_items_unit_cost_non_negative CHECK (unit_cost >= 0)
);

CREATE INDEX idx_purchase_orders_supplier_created
    ON purchase_orders (supplier_id, created_at DESC);
CREATE INDEX idx_purchase_orders_warehouse_status
    ON purchase_orders (destination_warehouse_id, status);
CREATE INDEX idx_purchase_orders_status_created
    ON purchase_orders (status, created_at DESC);
CREATE INDEX idx_purchase_orders_folio_lower ON purchase_orders (lower(folio));
CREATE INDEX idx_purchase_order_items_order
    ON purchase_order_items (purchase_order_id);
CREATE INDEX idx_purchase_order_items_product
    ON purchase_order_items (product_id);
CREATE INDEX idx_purchase_receipts_order_received
    ON purchase_receipts (purchase_order_id, received_at, id);
CREATE INDEX idx_purchase_receipt_items_order_item
    ON purchase_receipt_items (purchase_order_item_id);

ALTER TABLE stock_movements
    DROP CONSTRAINT ck_stock_movements_type,
    DROP CONSTRAINT ck_stock_movements_reservation_semantics,
    ADD CONSTRAINT ck_stock_movements_type CHECK (movement_type IN (
        'INITIAL_STOCK', 'MANUAL_IN', 'MANUAL_OUT',
        'ORDER_RESERVED', 'ORDER_RESERVATION_RELEASED',
        'ORDER_CONFIRMED', 'ORDER_CANCELLED', 'PURCHASE_RECEIVED'
    )),
    ADD CONSTRAINT ck_stock_movements_reservation_semantics CHECK (
        (movement_type = 'ORDER_RESERVED'
            AND quantity_delta = 0 AND reservation_delta > 0)
        OR
        (movement_type = 'ORDER_RESERVATION_RELEASED'
            AND quantity_delta = 0 AND reservation_delta < 0)
        OR
        (movement_type = 'ORDER_CONFIRMED'
            AND quantity_delta < 0 AND reservation_delta = quantity_delta)
        OR
        (movement_type IN (
            'INITIAL_STOCK', 'MANUAL_IN', 'MANUAL_OUT',
            'ORDER_CANCELLED', 'PURCHASE_RECEIVED'
        ) AND reservation_delta = 0)
    );

CREATE UNIQUE INDEX uk_stock_movements_purchase_receipt
    ON stock_movements (
        warehouse_id, product_id, movement_type, business_reference
    )
    WHERE movement_type = 'PURCHASE_RECEIVED';
