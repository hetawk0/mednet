CREATE TABLE consultations (
    id VARCHAR(36) PRIMARY KEY,
    appointment_id VARCHAR(36) NOT NULL UNIQUE
        REFERENCES appointments (id) ON DELETE RESTRICT,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'ENDED')),
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE consultation_messages (
    id VARCHAR(36) PRIMARY KEY,
    consultation_id VARCHAR(36) NOT NULL
        REFERENCES consultations (id) ON DELETE RESTRICT,
    sender_account_id VARCHAR(36) NOT NULL
        REFERENCES platform_accounts (id) ON DELETE RESTRICT,
    body VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_consultation_messages_session_created
    ON consultation_messages (consultation_id, created_at DESC);
