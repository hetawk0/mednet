ALTER TABLE platform_accounts DROP CONSTRAINT IF EXISTS platform_accounts_account_type_check;

ALTER TABLE platform_accounts
    ADD CONSTRAINT platform_accounts_account_type_check
    CHECK (account_type IN ('PATIENT', 'PROVIDER', 'ADMIN', 'SUPER_ADMIN'));