ALTER TABLE orders
    DROP CONSTRAINT ck_orders_status,
    DROP CONSTRAINT ck_orders_lifecycle_audit,
    ADD COLUMN reserved_at TIMESTAMPTZ,
    ADD COLUMN reserved_by VARCHAR(255);

UPDATE orders
SET reserved_at = confirmed_at,
    reserved_by = confirmed_by
WHERE status IN ('CONFIRMED', 'CANCELLED');

ALTER TABLE orders
    ADD CONSTRAINT ck_orders_status
        CHECK (status IN ('PENDING', 'RESERVED', 'CONFIRMED', 'CANCELLED')),
    ADD CONSTRAINT ck_orders_lifecycle_audit CHECK (
        (status = 'PENDING'
            AND reserved_at IS NULL AND reserved_by IS NULL
            AND confirmed_at IS NULL AND confirmed_by IS NULL
            AND cancelled_at IS NULL AND cancelled_by IS NULL)
        OR
        (status = 'RESERVED'
            AND reserved_at IS NOT NULL AND reserved_by IS NOT NULL
            AND confirmed_at IS NULL AND confirmed_by IS NULL
            AND cancelled_at IS NULL AND cancelled_by IS NULL)
        OR
        (status = 'CONFIRMED'
            AND reserved_at IS NOT NULL AND reserved_by IS NOT NULL
            AND confirmed_at IS NOT NULL AND confirmed_by IS NOT NULL
            AND cancelled_at IS NULL AND cancelled_by IS NULL)
        OR
        (status = 'CANCELLED'
            AND reserved_at IS NOT NULL AND reserved_by IS NOT NULL
            AND confirmed_at IS NOT NULL AND confirmed_by IS NOT NULL
            AND cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL)
    );

CREATE TABLE inventory_reservations (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    product_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    reserved_at TIMESTAMPTZ NOT NULL,
    reserved_by VARCHAR(255) NOT NULL,
    CONSTRAINT fk_inventory_reservations_order
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_inventory_reservations_product
        FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT uk_inventory_reservations_order_product
        UNIQUE (order_id, product_id),
    CONSTRAINT ck_inventory_reservations_quantity_positive
        CHECK (quantity > 0)
);

CREATE INDEX idx_inventory_reservations_product
    ON inventory_reservations (product_id);
CREATE INDEX idx_inventory_reservations_order
    ON inventory_reservations (order_id);

ALTER TABLE stock_movements
    DROP CONSTRAINT ck_stock_movements_type,
    DROP CONSTRAINT ck_stock_movements_delta_non_zero,
    DROP CONSTRAINT ck_stock_movements_balance_consistent,
    DROP CONSTRAINT uk_stock_movements_business_event,
    ADD COLUMN reservation_delta INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN reserved_before INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN reserved_after INTEGER NOT NULL DEFAULT 0;

UPDATE stock_movements
SET reservation_delta = quantity_delta,
    reserved_before = -quantity_delta,
    reserved_after = 0
WHERE movement_type = 'ORDER_CONFIRMED';

ALTER TABLE stock_movements
    ADD CONSTRAINT ck_stock_movements_type CHECK (movement_type IN (
        'INITIAL_STOCK', 'MANUAL_IN', 'MANUAL_OUT',
        'ORDER_RESERVED', 'ORDER_RESERVATION_RELEASED',
        'ORDER_CONFIRMED', 'ORDER_CANCELLED'
    )),
    ADD CONSTRAINT ck_stock_movements_non_zero_effect
        CHECK (quantity_delta <> 0 OR reservation_delta <> 0),
    ADD CONSTRAINT ck_stock_movements_balance_consistent
        CHECK (balance_after = balance_before + quantity_delta),
    ADD CONSTRAINT ck_stock_movements_reserved_balances_non_negative
        CHECK (reserved_before >= 0 AND reserved_after >= 0),
    ADD CONSTRAINT ck_stock_movements_reserved_balance_consistent
        CHECK (reserved_after = reserved_before + reservation_delta),
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
            'INITIAL_STOCK', 'MANUAL_IN', 'MANUAL_OUT', 'ORDER_CANCELLED'
        ) AND reservation_delta = 0)
    );

CREATE UNIQUE INDEX uk_stock_movements_final_order_event
    ON stock_movements (product_id, movement_type, business_reference)
    WHERE movement_type IN ('ORDER_CONFIRMED', 'ORDER_CANCELLED');
