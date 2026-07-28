CREATE TABLE orders (
    id UUID PRIMARY KEY,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_orders_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED'))
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    product_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product
        FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT uk_order_items_order_product UNIQUE (order_id, product_id),
    CONSTRAINT ck_order_items_quantity_positive CHECK (quantity > 0)
);

CREATE TABLE stock_movements (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    movement_type VARCHAR(32) NOT NULL,
    quantity_delta INTEGER NOT NULL,
    balance_before INTEGER NOT NULL,
    balance_after INTEGER NOT NULL,
    business_reference VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    responsible_user VARCHAR(255),
    CONSTRAINT fk_stock_movements_product
        FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_stock_movements_type CHECK (movement_type IN (
        'INITIAL_STOCK', 'MANUAL_IN', 'MANUAL_OUT',
        'ORDER_CONFIRMED', 'ORDER_CANCELLED'
    )),
    CONSTRAINT ck_stock_movements_delta_non_zero CHECK (quantity_delta <> 0),
    CONSTRAINT ck_stock_movements_balances_non_negative
        CHECK (balance_before >= 0 AND balance_after >= 0),
    CONSTRAINT ck_stock_movements_balance_consistent
        CHECK (balance_after = balance_before + quantity_delta),
    CONSTRAINT uk_stock_movements_business_event
        UNIQUE (product_id, movement_type, business_reference)
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_order_items_product_id ON order_items (product_id);
CREATE INDEX idx_stock_movements_product_occurred
    ON stock_movements (product_id, occurred_at);
CREATE INDEX idx_stock_movements_business_reference
    ON stock_movements (business_reference);
