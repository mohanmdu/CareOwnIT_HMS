-- Local-dev provisional migration (see note in V1__baseline.sql).

ALTER TABLE op_case_sheet
    MODIFY COLUMN appointment_id BIGINT NULL,
    ADD COLUMN op_direct_billing_id BIGINT NULL,
    ADD CONSTRAINT uq_op_case_sheet_op_direct_billing UNIQUE (op_direct_billing_id),
    ADD CONSTRAINT fk_op_case_sheet_op_direct_billing FOREIGN KEY (op_direct_billing_id) REFERENCES op_direct_billing (id),
    ADD CONSTRAINT chk_op_case_sheet_source CHECK (
        (appointment_id IS NOT NULL AND op_direct_billing_id IS NULL) OR
        (appointment_id IS NULL AND op_direct_billing_id IS NOT NULL)
    );
