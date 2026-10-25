ALTER TABLE consultations
    ADD COLUMN outcome_record_id VARCHAR(36) UNIQUE
        REFERENCES clinical_records (id) ON DELETE RESTRICT;
