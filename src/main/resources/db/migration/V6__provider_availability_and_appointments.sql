CREATE TABLE provider_availability_slots (
    id VARCHAR(36) PRIMARY KEY,
    provider_application_id VARCHAR(36) NOT NULL
        REFERENCES provider_applications (id) ON DELETE CASCADE,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'RESERVED', 'CLOSED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_provider_availability_range CHECK (ends_at > starts_at)
);

CREATE INDEX idx_provider_availability_provider_start
    ON provider_availability_slots (provider_application_id, starts_at);

CREATE TABLE appointments (
    id VARCHAR(36) PRIMARY KEY,
    patient_account_id VARCHAR(36) NOT NULL
        REFERENCES platform_accounts (id) ON DELETE CASCADE,
    provider_application_id VARCHAR(36) NOT NULL
        REFERENCES provider_applications (id) ON DELETE CASCADE,
    availability_slot_id VARCHAR(36) NOT NULL
        REFERENCES provider_availability_slots (id),
    requested_availability_slot_id VARCHAR(36)
        REFERENCES provider_availability_slots (id),
    reschedule_requested_by_email VARCHAR(254),
    status VARCHAR(24) NOT NULL
        CHECK (status IN ('PENDING', 'CONFIRMED', 'RESCHEDULE_REQUESTED',
                          'DECLINED', 'CANCELLED', 'COMPLETED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_appointments_patient_created
    ON appointments (patient_account_id, created_at DESC);

CREATE INDEX idx_appointments_provider_created
    ON appointments (provider_application_id, created_at DESC);

CREATE INDEX idx_appointments_slot_status
    ON appointments (availability_slot_id, status);
