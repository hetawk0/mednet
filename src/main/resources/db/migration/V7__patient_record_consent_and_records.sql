CREATE TABLE patient_provider_record_consents (
    id VARCHAR(36) PRIMARY KEY,
    patient_account_id VARCHAR(36) NOT NULL
        REFERENCES platform_accounts (id) ON DELETE RESTRICT,
    provider_application_id VARCHAR(36) NOT NULL
        REFERENCES provider_applications (id) ON DELETE RESTRICT,
    status VARCHAR(16) NOT NULL
        CHECK (status IN ('GRANTED', 'REVOKED')),
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_record_consent_patient_provider UNIQUE (patient_account_id, provider_application_id)
);

CREATE INDEX idx_record_consent_provider_status
    ON patient_provider_record_consents (provider_application_id, status);

CREATE TABLE clinical_records (
    id VARCHAR(36) PRIMARY KEY,
    patient_account_id VARCHAR(36) NOT NULL
        REFERENCES platform_accounts (id) ON DELETE RESTRICT,
    author_account_id VARCHAR(36) NOT NULL
        REFERENCES platform_accounts (id) ON DELETE RESTRICT,
    provider_application_id VARCHAR(36) NOT NULL
        REFERENCES provider_applications (id) ON DELETE RESTRICT,
    category VARCHAR(32) NOT NULL
        CHECK (category IN ('ENCOUNTER_SUMMARY', 'DIAGNOSIS', 'PRESCRIPTION',
                            'ALLERGY', 'MEDICAL_HISTORY', 'DOCUMENT')),
    title VARCHAR(160) NOT NULL,
    clinical_code VARCHAR(80),
    summary VARCHAR(10000) NOT NULL,
    effective_at TIMESTAMP WITH TIME ZONE NOT NULL,
    amends_record_id VARCHAR(36)
        REFERENCES clinical_records (id) ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_clinical_records_patient_effective
    ON clinical_records (patient_account_id, effective_at DESC, created_at DESC);

CREATE INDEX idx_clinical_records_provider
    ON clinical_records (provider_application_id, patient_account_id);
