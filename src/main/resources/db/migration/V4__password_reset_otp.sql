ALTER TABLE platform_accounts
    ADD COLUMN password_reset_verified BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE platform_accounts
    ADD COLUMN password_reset_attempts INTEGER NOT NULL DEFAULT 0;