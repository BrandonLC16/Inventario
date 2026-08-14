CREATE TABLE suppliers (
    id UUID PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    legal_name VARCHAR(160) NOT NULL,
    commercial_name VARCHAR(160),
    fiscal_identifier VARCHAR(32),
    email VARCHAR(254),
    phone VARCHAR(32),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_suppliers_code UNIQUE (code),
    CONSTRAINT ck_suppliers_code_normalized CHECK (
        code = upper(btrim(code)) AND length(btrim(code)) > 0
    ),
    CONSTRAINT ck_suppliers_legal_name_not_blank CHECK (
        length(btrim(legal_name)) > 0
    ),
    CONSTRAINT ck_suppliers_commercial_name_not_blank CHECK (
        commercial_name IS NULL OR length(btrim(commercial_name)) > 0
    ),
    CONSTRAINT ck_suppliers_fiscal_identifier_normalized CHECK (
        fiscal_identifier IS NULL
            OR fiscal_identifier = upper(btrim(fiscal_identifier))
    ),
    CONSTRAINT ck_suppliers_email_normalized CHECK (
        email IS NULL
            OR (email = lower(btrim(email)) AND position('@' IN email) > 1)
    ),
    CONSTRAINT ck_suppliers_phone_not_blank CHECK (
        phone IS NULL OR length(btrim(phone)) > 0
    )
);

CREATE UNIQUE INDEX uk_suppliers_fiscal_identifier
    ON suppliers (fiscal_identifier) WHERE fiscal_identifier IS NOT NULL;
CREATE UNIQUE INDEX uk_suppliers_email_lower
    ON suppliers (lower(email)) WHERE email IS NOT NULL;
CREATE INDEX idx_suppliers_legal_name_lower ON suppliers (lower(legal_name));
CREATE INDEX idx_suppliers_commercial_name_lower
    ON suppliers (lower(commercial_name)) WHERE commercial_name IS NOT NULL;
CREATE INDEX idx_suppliers_active ON suppliers (active);

CREATE TABLE supplier_products (
    supplier_id UUID NOT NULL,
    product_id UUID NOT NULL,
    supplier_sku VARCHAR(64),
    lead_time_days INTEGER NOT NULL,
    minimum_order_quantity INTEGER NOT NULL,
    last_unit_cost NUMERIC(14, 4),
    preferred BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_supplier_products PRIMARY KEY (supplier_id, product_id),
    CONSTRAINT fk_supplier_products_supplier
        FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT fk_supplier_products_product
        FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_supplier_products_supplier_sku_not_blank CHECK (
        supplier_sku IS NULL OR length(btrim(supplier_sku)) > 0
    ),
    CONSTRAINT ck_supplier_products_lead_time_non_negative CHECK (
        lead_time_days >= 0
    ),
    CONSTRAINT ck_supplier_products_minimum_order_positive CHECK (
        minimum_order_quantity > 0
    ),
    CONSTRAINT ck_supplier_products_last_unit_cost_non_negative CHECK (
        last_unit_cost IS NULL OR last_unit_cost >= 0
    ),
    CONSTRAINT ck_supplier_products_preferred_active CHECK (
        NOT preferred OR active
    )
);

CREATE INDEX idx_supplier_products_product
    ON supplier_products (product_id);
CREATE INDEX idx_supplier_products_supplier_active
    ON supplier_products (supplier_id, active);
CREATE UNIQUE INDEX uk_supplier_products_preferred_product
    ON supplier_products (product_id) WHERE preferred;
