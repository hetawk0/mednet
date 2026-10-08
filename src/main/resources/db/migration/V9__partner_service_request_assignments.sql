ALTER TABLE platform_accounts DROP CONSTRAINT IF EXISTS platform_accounts_account_type_check;
ALTER TABLE platform_accounts DROP CONSTRAINT IF EXISTS CONSTRAINT_9186;

ALTER TABLE platform_accounts
    ADD CONSTRAINT platform_accounts_account_type_check
    CHECK (account_type IN ('PATIENT', 'PROVIDER', 'ADMIN', 'SUPER_ADMIN', 'LABORATORY', 'HOME_CARE'));

ALTER TABLE patient_service_requests
    ADD COLUMN assigned_staff_account_id VARCHAR(36)
        REFERENCES platform_accounts (id) ON DELETE RESTRICT;

CREATE INDEX idx_patient_service_requests_staff_type_created
    ON patient_service_requests (assigned_staff_account_id, request_type, created_at DESC);
