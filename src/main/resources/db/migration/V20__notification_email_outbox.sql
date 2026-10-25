ALTER TABLE platform_accounts
    ADD COLUMN email_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE notification_email_outbox (
    id VARCHAR(36) PRIMARY KEY,
    notification_id VARCHAR(36) NOT NULL UNIQUE REFERENCES user_notifications(id) ON DELETE CASCADE,
    recipient_account_id VARCHAR(36) NOT NULL REFERENCES platform_accounts(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'SUPPRESSED', 'FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_expires_at TIMESTAMP WITH TIME ZONE,
    last_error_code VARCHAR(80),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_notification_email_outbox_ready
    ON notification_email_outbox (status, next_attempt_at, created_at);
