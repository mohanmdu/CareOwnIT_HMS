ALTER TABLE op_direct_billing ADD COLUMN consultant_id BIGINT NULL;
ALTER TABLE op_direct_billing ADD CONSTRAINT fk_op_direct_billing_consultant FOREIGN KEY (consultant_id) REFERENCES consultant(id);
