CREATE INDEX idx_accounts_reporting
    ON platform_accounts (account_type, created_at, status);

CREATE INDEX idx_appointments_reporting
    ON appointments (created_at, status, availability_slot_id);

CREATE INDEX idx_patient_service_requests_reporting
    ON patient_service_requests (created_at, request_type, status);

CREATE INDEX idx_laboratory_results_reporting
    ON laboratory_results (created_at, status);

CREATE INDEX idx_consultations_reporting
    ON consultations (status, ended_at, appointment_id);
