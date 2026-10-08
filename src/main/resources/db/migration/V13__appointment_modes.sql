ALTER TABLE provider_availability_slots
    ADD COLUMN consultation_mode VARCHAR(16) NOT NULL DEFAULT 'TEXT'
        CHECK (consultation_mode IN ('IN_PERSON', 'TEXT'));
