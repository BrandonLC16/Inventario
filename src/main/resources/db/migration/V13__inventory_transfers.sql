CREATE SEQUENCE inventory_transfers_folio_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE inventory_transfers (
    id UUID PRIMARY KEY,
    folio VARCHAR(32) NOT NULL,
    source_warehouse_id UUID NOT NULL,
    destination_warehouse_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    dispatched_at TIMESTAMPTZ,
    dispatched_by VARCHAR(255),
    received_at TIMESTAMPTZ,
    received_by VARCHAR(255),
    cancelled_at TIMESTAMPTZ,
    cancelled_by VARCHAR(255),
    CONSTRAINT uk_inventory_transfers_folio UNIQUE (folio),
    CONSTRAINT fk_inventory_transfers_source_warehouse
        FOREIGN KEY (source_warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_inventory_transfers_destination_warehouse
        FOREIGN KEY (destination_warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT ck_inventory_transfers_different_warehouses
        CHECK (source_warehouse_id <> destination_warehouse_id),
    CONSTRAINT ck_inventory_transfers_status CHECK (status IN (
        'DRAFT', 'IN_TRANSIT', 'RECEIVED', 'CANCELLED'
    )),
    CONSTRAINT ck_inventory_transfers_lifecycle_audit CHECK (
        (status = 'DRAFT'
            AND dispatched_at IS NULL AND dispatched_by IS NULL
            AND received_at IS NULL AND received_by IS NULL
            AND cancelled_at IS NULL AND cancelled_by IS NULL)
        OR
        (status = 'IN_TRANSIT'
            AND dispatched_at IS NOT NULL AND dispatched_by IS NOT NULL
            AND received_at IS NULL AND received_by IS NULL
            AND cancelled_at IS NULL AND cancelled_by IS NULL)
        OR
        (status = 'RECEIVED'
            AND dispatched_at IS NOT NULL AND dispatched_by IS NOT NULL
            AND received_at IS NOT NULL AND received_by IS NOT NULL
            AND cancelled_at IS NULL AND cancelled_by IS NULL)
        OR
        (status = 'CANCELLED'
            AND dispatched_at IS NULL AND dispatched_by IS NULL
            AND received_at IS NULL AND received_by IS NULL
            AND cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL)
    )
);

CREATE TABLE inventory_transfer_items (
    id UUID PRIMARY KEY,
    transfer_id UUID NOT NULL,
    product_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    CONSTRAINT fk_inventory_transfer_items_transfer
        FOREIGN KEY (transfer_id) REFERENCES inventory_transfers (id) ON DELETE CASCADE,
    CONSTRAINT fk_inventory_transfer_items_product
        FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT uk_inventory_transfer_items_transfer_product
        UNIQUE (transfer_id, product_id),
    CONSTRAINT ck_inventory_transfer_items_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX idx_inventory_transfers_status_created
    ON inventory_transfers (status, created_at DESC);
CREATE INDEX idx_inventory_transfers_source_status
    ON inventory_transfers (source_warehouse_id, status);
CREATE INDEX idx_inventory_transfers_destination_status
    ON inventory_transfers (destination_warehouse_id, status);
CREATE INDEX idx_inventory_transfers_folio_lower
    ON inventory_transfers (lower(folio));
CREATE INDEX idx_inventory_transfer_items_transfer
    ON inventory_transfer_items (transfer_id);
CREATE INDEX idx_inventory_transfer_items_product
    ON inventory_transfer_items (product_id);

ALTER TABLE stock_movements
    DROP CONSTRAINT ck_stock_movements_type,
    DROP CONSTRAINT ck_stock_movements_reservation_semantics,
    ADD CONSTRAINT ck_stock_movements_type CHECK (movement_type IN (
        'INITIAL_STOCK', 'MANUAL_IN', 'MANUAL_OUT',
        'ORDER_RESERVED', 'ORDER_RESERVATION_RELEASED',
        'ORDER_CONFIRMED', 'ORDER_CANCELLED', 'PURCHASE_RECEIVED',
        'TRANSFER_OUT', 'TRANSFER_IN'
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
        (movement_type = 'TRANSFER_OUT'
            AND quantity_delta < 0 AND reservation_delta = 0)
        OR
        (movement_type = 'TRANSFER_IN'
            AND quantity_delta > 0 AND reservation_delta = 0)
        OR
        (movement_type IN (
            'INITIAL_STOCK', 'MANUAL_IN', 'MANUAL_OUT',
            'ORDER_CANCELLED', 'PURCHASE_RECEIVED'
        ) AND reservation_delta = 0)
    );

CREATE UNIQUE INDEX uk_stock_movements_transfer_event
    ON stock_movements (
        warehouse_id, product_id, movement_type, business_reference
    )
    WHERE movement_type IN ('TRANSFER_OUT', 'TRANSFER_IN');
