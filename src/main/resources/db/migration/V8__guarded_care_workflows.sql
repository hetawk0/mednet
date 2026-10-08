ALTER TABLE clinical_records ALTER COLUMN provider_application_id DROP NOT NULL;
ALTER TABLE clinical_records DROP CONSTRAINT IF EXISTS clinical_records_category_check;
ALTER TABLE clinical_records DROP CONSTRAINT IF EXISTS CONSTRAINT_F6D704;
ALTER TABLE clinical_records ADD CONSTRAINT chk_clinical_records_category
    CHECK (category IN ('ENCOUNTER_SUMMARY', 'DIAGNOSIS', 'PRESCRIPTION', 'ALLERGY',
                        'MEDICAL_HISTORY', 'DOCUMENT', 'MEDICATION', 'VITAL'));

CREATE TABLE patient_conversations (
    id VARCHAR(36) PRIMARY KEY,
    patient_account_id VARCHAR(36) NOT NULL
        REFERENCES platform_accounts (id) ON DELETE RESTRICT,
    provider_application_id VARCHAR(36) NOT NULL
        REFERENCES provider_applications (id) ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_patient_conversation UNIQUE (patient_account_id, provider_application_id)
);

CREATE TABLE patient_messages (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL
        REFERENCES patient_conversations (id) ON DELETE RESTRICT,
    sender_account_id VARCHAR(36) NOT NULL
        REFERENCES platform_accounts (id) ON DELETE RESTRICT,
    body VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_patient_messages_conversation_created
    ON patient_messages (conversation_id, created_at);

CREATE TABLE user_notifications (
    id VARCHAR(36) PRIMARY KEY,
    recipient_account_id VARCHAR(36) NOT NULL
        REFERENCES platform_accounts (id) ON DELETE RESTRICT,
    event_key VARCHAR(160) NOT NULL UNIQUE,
    event_type VARCHAR(40) NOT NULL,
    title VARCHAR(160) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_user_notifications_recipient_created
    ON user_notifications (recipient_account_id, created_at DESC);

CREATE TABLE medication_schedules (
    id VARCHAR(36) PRIMARY KEY,
    patient_account_id VARCHAR(36) NOT NULL
        REFERENCES platform_accounts (id) ON DELETE RESTRICT,
    medication_name VARCHAR(160) NOT NULL,
    dose VARCHAR(120) NOT NULL,
    reminder_time TIME NOT NULL,
    time_zone VARCHAR(80) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    next_reminder_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'STOPPED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_medication_schedule_dates CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX idx_medication_schedules_due
    ON medication_schedules (status, next_reminder_at);

CREATE TABLE medication_dose_logs (
    id VARCHAR(36) PRIMARY KEY,
    schedule_id VARCHAR(36) NOT NULL
        REFERENCES medication_schedules (id) ON DELETE RESTRICT,
    dose_date DATE NOT NULL,
    taken_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_medication_dose_log UNIQUE (schedule_id, dose_date)
);

CREATE TABLE patient_vitals (
    id VARCHAR(36) PRIMARY KEY,
    patient_account_id VARCHAR(36) NOT NULL
        REFERENCES platform_accounts (id) ON DELETE RESTRICT,
    author_account_id VARCHAR(36) NOT NULL
        REFERENCES platform_accounts (id) ON DELETE RESTRICT,
    metric VARCHAR(80) NOT NULL,
    reading_value NUMERIC(12, 3) NOT NULL,
    unit VARCHAR(40) NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    source VARCHAR(24) NOT NULL DEFAULT 'PATIENT_REPORTED'
        CHECK (source IN ('PATIENT_REPORTED')),
    clinical_record_id VARCHAR(36) NOT NULL
        REFERENCES clinical_records (id) ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_patient_vitals_patient_recorded
    ON patient_vitals (patient_account_id, recorded_at DESC);

CREATE TABLE patient_service_requests (
    id VARCHAR(36) PRIMARY KEY,
    patient_account_id VARCHAR(36) NOT NULL
        REFERENCES platform_accounts (id) ON DELETE RESTRICT,
    request_type VARCHAR(20) NOT NULL
        CHECK (request_type IN ('HOME_CARE', 'LABORATORY')),
    requested_service VARCHAR(240) NOT NULL,
    location_description VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CANCELLED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_patient_service_requests_patient_created
    ON patient_service_requests (patient_account_id, created_at DESC);

CREATE INDEX idx_patient_service_requests_type_status
    ON patient_service_requests (request_type, status, created_at DESC);
