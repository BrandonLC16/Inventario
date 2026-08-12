CREATE SEQUENCE orders_folio_seq START WITH 1 INCREMENT BY 1;

ALTER TABLE orders
    ADD COLUMN folio VARCHAR(32),
    ADD COLUMN customer_id UUID,
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'MXN',
    ADD COLUMN total NUMERIC(20, 2),
    ADD COLUMN created_by VARCHAR(255),
    ADD COLUMN confirmed_at TIMESTAMPTZ,
    ADD COLUMN confirmed_by VARCHAR(255),
    ADD COLUMN cancelled_at TIMESTAMPTZ,
    ADD COLUMN cancelled_by VARCHAR(255);

ALTER TABLE order_items
    ADD COLUMN unit_price NUMERIC(12, 2),
    ADD COLUMN subtotal NUMERIC(20, 2);

UPDATE order_items item
SET unit_price = product.price,
    subtotal = product.price * item.quantity
FROM products product
WHERE product.id = item.product_id;

UPDATE orders sales_order
SET folio = 'ORD-LEGACY-' || upper(substr(replace(sales_order.id::text, '-', ''), 1, 12)),
    total = COALESCE((
        SELECT sum(item.subtotal)
        FROM order_items item
        WHERE item.order_id = sales_order.id
    ), 0),
    created_by = 'SYSTEM_MIGRATION',
    confirmed_at = CASE
        WHEN sales_order.status IN ('CONFIRMED', 'CANCELLED') THEN sales_order.updated_at
        ELSE NULL
    END,
    confirmed_by = CASE
        WHEN sales_order.status IN ('CONFIRMED', 'CANCELLED') THEN 'SYSTEM_MIGRATION'
        ELSE NULL
    END,
    cancelled_at = CASE
        WHEN sales_order.status = 'CANCELLED' THEN sales_order.updated_at
        ELSE NULL
    END,
    cancelled_by = CASE
        WHEN sales_order.status = 'CANCELLED' THEN 'SYSTEM_MIGRATION'
        ELSE NULL
    END;

ALTER TABLE orders
    ALTER COLUMN folio SET NOT NULL,
    ALTER COLUMN total SET NOT NULL,
    ALTER COLUMN created_by SET NOT NULL,
    ALTER COLUMN currency DROP DEFAULT,
    ADD CONSTRAINT uk_orders_folio UNIQUE (folio),
    ADD CONSTRAINT fk_orders_customer
        FOREIGN KEY (customer_id) REFERENCES customers (id),
    ADD CONSTRAINT ck_orders_currency CHECK (currency ~ '^[A-Z]{3}$'),
    ADD CONSTRAINT ck_orders_total_non_negative CHECK (total >= 0),
    ADD CONSTRAINT ck_orders_lifecycle_audit CHECK (
        (status = 'PENDING'
            AND confirmed_at IS NULL AND confirmed_by IS NULL
            AND cancelled_at IS NULL AND cancelled_by IS NULL)
        OR
        (status = 'CONFIRMED'
            AND confirmed_at IS NOT NULL AND confirmed_by IS NOT NULL
            AND cancelled_at IS NULL AND cancelled_by IS NULL)
        OR
        (status = 'CANCELLED'
            AND confirmed_at IS NOT NULL AND confirmed_by IS NOT NULL
            AND cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL)
    );

ALTER TABLE order_items
    ALTER COLUMN unit_price SET NOT NULL,
    ALTER COLUMN subtotal SET NOT NULL,
    ADD CONSTRAINT ck_order_items_unit_price_non_negative CHECK (unit_price >= 0),
    ADD CONSTRAINT ck_order_items_subtotal_consistent
        CHECK (subtotal = unit_price * quantity);

CREATE INDEX idx_orders_customer_created ON orders (customer_id, created_at DESC);
CREATE INDEX idx_orders_status_created ON orders (status, created_at DESC);
CREATE INDEX idx_orders_folio_lower ON orders (lower(folio));
