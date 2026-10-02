CREATE TABLE provider_applications (
    id VARCHAR(36) PRIMARY KEY,
    display_name VARCHAR(160) NOT NULL,
    email VARCHAR(254) NOT NULL,
    specialty VARCHAR(120) NOT NULL,
    credential_reference VARCHAR(120) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    reviewed_by VARCHAR(254)
);

CREATE INDEX idx_provider_applications_status_created
    ON provider_applications (status, created_at DESC);

CREATE TABLE platform_accounts (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    google_subject VARCHAR(255) UNIQUE,
    account_type VARCHAR(16) NOT NULL
        CHECK (account_type IN ('PATIENT', 'PROVIDER')),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_platform_accounts_status_created
    ON platform_accounts (status, created_at DESC);

CREATE TABLE service_requests (
    id VARCHAR(36) PRIMARY KEY,
    reference_id VARCHAR(80) NOT NULL UNIQUE,
    request_type VARCHAR(20) NOT NULL
        CHECK (request_type IN ('APPOINTMENT', 'HOME_CARE', 'LABORATORY')),
    requester_email VARCHAR(254) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CANCELLED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_service_requests_status_created
    ON service_requests (status, created_at DESC);

CREATE TABLE admin_audit_events (
    id VARCHAR(36) PRIMARY KEY,
    actor_email VARCHAR(254) NOT NULL,
    action VARCHAR(40) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_admin_audit_events_created
    ON admin_audit_events (created_at DESC);