CREATE TABLE customers (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    fiscal_identifier VARCHAR(32),
    email VARCHAR(254),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_customers_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_customers_fiscal_identifier_normalized CHECK (
        fiscal_identifier IS NULL OR fiscal_identifier = upper(btrim(fiscal_identifier))
    ),
    CONSTRAINT ck_customers_email_normalized CHECK (
        email IS NULL OR (email = lower(btrim(email)) AND position('@' IN email) > 1)
    )
);

CREATE UNIQUE INDEX uk_customers_fiscal_identifier
    ON customers (fiscal_identifier) WHERE fiscal_identifier IS NOT NULL;
CREATE UNIQUE INDEX uk_customers_email_lower
    ON customers (lower(email)) WHERE email IS NOT NULL;
CREATE INDEX idx_customers_name_lower ON customers (lower(name));
CREATE INDEX idx_customers_active ON customers (active);
