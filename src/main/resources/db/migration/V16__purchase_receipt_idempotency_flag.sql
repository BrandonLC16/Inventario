ALTER TABLE purchase_receipts
    ADD COLUMN update_supplier_product_last_cost BOOLEAN;

COMMENT ON COLUMN purchase_receipts.update_supplier_product_last_cost IS
    'NULL only for receipts created before V16, whose original idempotency flag is unknown';
