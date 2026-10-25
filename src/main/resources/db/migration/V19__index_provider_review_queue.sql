CREATE INDEX idx_appointments_provider_status_patient
    ON appointments (provider_application_id, status, patient_account_id);
