ALTER TABLE clinical_records DROP CONSTRAINT IF EXISTS chk_clinical_records_category;
ALTER TABLE clinical_records DROP CONSTRAINT IF EXISTS clinical_records_category_check;
ALTER TABLE clinical_records DROP CONSTRAINT IF EXISTS CONSTRAINT_F6D704;
ALTER TABLE clinical_records ADD CONSTRAINT chk_clinical_records_category
    CHECK (category IN ('ENCOUNTER_SUMMARY', 'DIAGNOSIS', 'PRESCRIPTION', 'ALLERGY',
                        'MEDICAL_HISTORY', 'DOCUMENT', 'MEDICATION', 'VITAL', 'LAB_RESULT'));

CREATE TABLE laboratory_results (
    id VARCHAR(36) PRIMARY KEY,
    request_id VARCHAR(36) NOT NULL
        REFERENCES patient_service_requests (id) ON DELETE RESTRICT,
    patient_account_id VARCHAR(36) NOT NULL
        REFERENCES platform_accounts (id) ON DELETE RESTRICT,
    author_account_id VARCHAR(36) NOT NULL
        REFERENCES platform_accounts (id) ON DELETE RESTRICT,
    summary VARCHAR(10000) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING_REVIEW'
        CHECK (status IN ('PENDING_REVIEW', 'RELEASED', 'RETURNED')),
    reviewer_account_id VARCHAR(36)
        REFERENCES platform_accounts (id) ON DELETE RESTRICT,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_laboratory_results_request_created
    ON laboratory_results (request_id, created_at DESC);

CREATE INDEX idx_laboratory_results_patient_status_created
    ON laboratory_results (patient_account_id, status, created_at DESC);
