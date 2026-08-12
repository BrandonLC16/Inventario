ALTER TABLE products
    ADD COLUMN minimum_stock INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_products_minimum_stock_non_negative CHECK (minimum_stock >= 0);

CREATE INDEX idx_products_replenishment
    ON products (active, deleted, minimum_stock);
