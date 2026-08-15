CREATE SEQUENCE inventory_counts_folio_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE inventory_counts (
    id UUID PRIMARY KEY,
    folio VARCHAR(32) NOT NULL,
    warehouse_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL,
    scope VARCHAR(16) NOT NULL,
    opened_at TIMESTAMPTZ,
    opened_by VARCHAR(255),
    submitted_at TIMESTAMPTZ,
    submitted_by VARCHAR(255),
    posted_at TIMESTAMPTZ,
    posted_by VARCHAR(255),
    cancelled_at TIMESTAMPTZ,
    cancelled_by VARCHAR(255),
    CONSTRAINT uk_inventory_counts_folio UNIQUE (folio),
    CONSTRAINT fk_inventory_counts_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT ck_inventory_counts_status CHECK (status IN (
        'DRAFT', 'OPEN', 'SUBMITTED', 'POSTED', 'CANCELLED'
    )),
    CONSTRAINT ck_inventory_counts_scope CHECK (scope IN ('FULL', 'SELECTED')),
    CONSTRAINT ck_inventory_counts_lifecycle_audit CHECK (
        (status = 'DRAFT'
            AND opened_at IS NULL AND opened_by IS NULL
            AND submitted_at IS NULL AND submitted_by IS NULL
            AND posted_at IS NULL AND posted_by IS NULL
            AND cancelled_at IS NULL AND cancelled_by IS NULL)
        OR
        (status = 'OPEN'
            AND opened_at IS NOT NULL AND opened_by IS NOT NULL
            AND submitted_at IS NULL AND submitted_by IS NULL
            AND posted_at IS NULL AND posted_by IS NULL
            AND cancelled_at IS NULL AND cancelled_by IS NULL)
        OR
        (status = 'SUBMITTED'
            AND opened_at IS NOT NULL AND opened_by IS NOT NULL
            AND submitted_at IS NOT NULL AND submitted_by IS NOT NULL
            AND posted_at IS NULL AND posted_by IS NULL
            AND cancelled_at IS NULL AND cancelled_by IS NULL)
        OR
        (status = 'POSTED'
            AND opened_at IS NOT NULL AND opened_by IS NOT NULL
            AND submitted_at IS NOT NULL AND submitted_by IS NOT NULL
            AND posted_at IS NOT NULL AND posted_by IS NOT NULL
            AND cancelled_at IS NULL AND cancelled_by IS NULL)
        OR
        (status = 'CANCELLED'
            AND posted_at IS NULL AND posted_by IS NULL
            AND cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL
            AND ((opened_at IS NULL AND opened_by IS NULL)
                OR (opened_at IS NOT NULL AND opened_by IS NOT NULL))
            AND ((submitted_at IS NULL AND submitted_by IS NULL)
                OR (submitted_at IS NOT NULL AND submitted_by IS NOT NULL))
            AND (submitted_at IS NULL OR opened_at IS NOT NULL))
    )
);

CREATE TABLE inventory_count_lines (
    id UUID PRIMARY KEY,
    count_id UUID NOT NULL,
    product_id UUID NOT NULL,
    expected_quantity INTEGER,
    counted_quantity INTEGER,
    variance INTEGER,
    counted_at TIMESTAMPTZ,
    counted_by VARCHAR(255),
    notes VARCHAR(1000),
    CONSTRAINT fk_inventory_count_lines_count
        FOREIGN KEY (count_id) REFERENCES inventory_counts (id) ON DELETE CASCADE,
    CONSTRAINT fk_inventory_count_lines_product
        FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT uk_inventory_count_lines_count_product UNIQUE (count_id, product_id),
    CONSTRAINT ck_inventory_count_lines_expected_non_negative
        CHECK (expected_quantity IS NULL OR expected_quantity >= 0),
    CONSTRAINT ck_inventory_count_lines_counted_non_negative
        CHECK (counted_quantity IS NULL OR counted_quantity >= 0),
    CONSTRAINT ck_inventory_count_lines_notes_not_blank
        CHECK (notes IS NULL OR length(btrim(notes)) > 0),
    CONSTRAINT ck_inventory_count_lines_capture_consistent CHECK (
        (counted_quantity IS NULL AND variance IS NULL
            AND counted_at IS NULL AND counted_by IS NULL)
        OR
        (counted_quantity IS NOT NULL AND expected_quantity IS NOT NULL
            AND variance = counted_quantity - expected_quantity
            AND counted_at IS NOT NULL AND counted_by IS NOT NULL)
    )
);

CREATE INDEX idx_inventory_counts_warehouse_status
    ON inventory_counts (warehouse_id, status);
CREATE INDEX idx_inventory_counts_status_folio
    ON inventory_counts (status, folio DESC);
CREATE INDEX idx_inventory_counts_folio_lower
    ON inventory_counts (lower(folio));
CREATE INDEX idx_inventory_count_lines_count
    ON inventory_count_lines (count_id);
CREATE INDEX idx_inventory_count_lines_product
    ON inventory_count_lines (product_id);

ALTER TABLE stock_movements
    DROP CONSTRAINT ck_stock_movements_type,
    DROP CONSTRAINT ck_stock_movements_reservation_semantics,
    ADD CONSTRAINT ck_stock_movements_type CHECK (movement_type IN (
        'INITIAL_STOCK', 'MANUAL_IN', 'MANUAL_OUT',
        'ORDER_RESERVED', 'ORDER_RESERVATION_RELEASED',
        'ORDER_CONFIRMED', 'ORDER_CANCELLED', 'PURCHASE_RECEIVED',
        'TRANSFER_OUT', 'TRANSFER_IN', 'PHYSICAL_COUNT_ADJUSTMENT'
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
            'ORDER_CANCELLED', 'PURCHASE_RECEIVED',
            'PHYSICAL_COUNT_ADJUSTMENT'
        ) AND reservation_delta = 0)
    );

CREATE UNIQUE INDEX uk_stock_movements_physical_count
    ON stock_movements (
        warehouse_id, product_id, movement_type, business_reference
    )
    WHERE movement_type = 'PHYSICAL_COUNT_ADJUSTMENT';
