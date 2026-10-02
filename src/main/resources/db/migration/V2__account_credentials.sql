ALTER TABLE platform_accounts ADD COLUMN password_hash VARCHAR(100);
ALTER TABLE platform_accounts ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE platform_accounts ADD COLUMN verification_token_hash VARCHAR(64);
ALTER TABLE platform_accounts ADD COLUMN verification_token_expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE platform_accounts ADD COLUMN password_reset_token_hash VARCHAR(64);
ALTER TABLE platform_accounts ADD COLUMN password_reset_token_expires_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_platform_accounts_verification_token
    ON platform_accounts (verification_token_hash);

CREATE INDEX idx_platform_accounts_password_reset_token
    ON platform_accounts (password_reset_token_hash);