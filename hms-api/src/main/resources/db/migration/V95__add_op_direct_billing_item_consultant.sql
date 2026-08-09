-- Per-item consultant attribution for OP Direct Billing (see
-- OpDirectBillingItem) - separate from the existing bill-level
-- op_direct_billing.consultant_id (V82), which stays for backward
-- compatibility with existing list/report screens.
ALTER TABLE op_direct_billing_item ADD COLUMN consultant_id BIGINT NULL;
ALTER TABLE op_direct_billing_item ADD CONSTRAINT fk_op_direct_billing_item_consultant FOREIGN KEY (consultant_id) REFERENCES consultant(id);
