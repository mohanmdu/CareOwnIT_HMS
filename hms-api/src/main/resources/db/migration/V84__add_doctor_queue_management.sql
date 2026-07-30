-- 1. Per-doctor configurable token prefix (Consultant Master), defaulting to
--    the uppercased first letter of the doctor's name if left blank. Strips
--    a leading "Dr."/"Dr " first - every consultant's name starts with
--    that, so a naive first-character default would give every doctor the
--    same prefix ("D").
ALTER TABLE consultant ADD COLUMN queue_token_prefix VARCHAR(10) NULL;
UPDATE consultant
    SET queue_token_prefix = UPPER(LEFT(TRIM(REGEXP_REPLACE(name, '^[Dd][Rr]\\.?\\s+', '')), 1))
    WHERE queue_token_prefix IS NULL;
ALTER TABLE consultant MODIFY COLUMN queue_token_prefix VARCHAR(10) NOT NULL;

-- 2. Dedicated per-(consultant, date) counter for safe, concurrent token
--    assignment - locked pessimistically at check-in time (see
--    DoctorQueueTokenService). Never exposed via any API.
CREATE TABLE doctor_queue_counter (
    id BIGINT NOT NULL AUTO_INCREMENT,
    consultant_id BIGINT NOT NULL,
    queue_date DATE NOT NULL,
    last_token_number INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_doctor_queue_counter_consultant_date UNIQUE (consultant_id, queue_date),
    CONSTRAINT fk_doctor_queue_counter_consultant FOREIGN KEY (consultant_id) REFERENCES consultant (id)
);

-- 3. The real, persisted queue entry - one row per checked-in appointment or
--    walk-in registration. appointment_id is NULL for walk-ins; the UNIQUE
--    constraint on it relies on MySQL/InnoDB allowing multiple NULLs while
--    still enforcing "at most one queue entry per appointment". "BOOKED" is
--    deliberately NOT a status value stored here - see DoctorQueueStatus.
CREATE TABLE doctor_queue_entry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    consultant_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    appointment_id BIGINT NULL,
    queue_date DATE NOT NULL,
    token_number INT NOT NULL,
    token_display VARCHAR(20) NOT NULL,
    source VARCHAR(16) NOT NULL,
    scheduled_slot_time TIME NULL,
    status VARCHAR(20) NOT NULL,
    priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    priority_reason VARCHAR(255) NULL,
    checked_in_at DATETIME(6) NOT NULL,
    called_at DATETIME(6) NULL,
    recall_count INT NOT NULL DEFAULT 0,
    consultation_started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    reason VARCHAR(255) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_doctor_queue_entry_appointment UNIQUE (appointment_id),
    CONSTRAINT uq_doctor_queue_entry_consultant_date_token UNIQUE (consultant_id, queue_date, token_number),
    CONSTRAINT fk_doctor_queue_entry_consultant FOREIGN KEY (consultant_id) REFERENCES consultant (id),
    CONSTRAINT fk_doctor_queue_entry_patient FOREIGN KEY (patient_id) REFERENCES patient (id),
    CONSTRAINT fk_doctor_queue_entry_appointment FOREIGN KEY (appointment_id) REFERENCES appointment (id)
);

-- Doctor Dashboard's live-queue query and the priority-ordered "next patient" query.
CREATE INDEX idx_doctor_queue_entry_consultant_date_status ON doctor_queue_entry (consultant_id, queue_date, status);
CREATE INDEX idx_doctor_queue_entry_patient ON doctor_queue_entry (patient_id);

-- 4. Audit trail, mirroring appointment_audit_log's shape/spirit.
CREATE TABLE doctor_queue_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    queue_entry_id BIGINT NULL,
    operation VARCHAR(32) NOT NULL,
    patient_name VARCHAR(255) NULL,
    consultant_name VARCHAR(255) NULL,
    queue_date DATE NULL,
    token_display VARCHAR(20) NULL,
    previous_value TEXT NULL,
    new_value TEXT NULL,
    performed_by VARCHAR(100) NULL,
    performed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_doctor_queue_audit_log_entry FOREIGN KEY (queue_entry_id) REFERENCES doctor_queue_entry (id)
);

CREATE INDEX idx_doctor_queue_audit_log_entry ON doctor_queue_audit_log (queue_entry_id);
CREATE INDEX idx_doctor_queue_audit_log_performed_at ON doctor_queue_audit_log (performed_at);
