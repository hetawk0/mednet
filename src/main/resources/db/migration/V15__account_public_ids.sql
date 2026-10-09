ALTER TABLE platform_accounts
    ADD COLUMN public_id VARCHAR(15);

UPDATE platform_accounts
SET public_id = CASE account_type
    WHEN 'PATIENT' THEN 'PT-'
    WHEN 'PROVIDER' THEN 'PR-'
    WHEN 'HOME_CARE' THEN 'HC-'
    WHEN 'LABORATORY' THEN 'LB-'
    WHEN 'ADMIN' THEN 'AD-'
    WHEN 'SUPER_ADMIN' THEN 'SA-'
    ELSE 'AC-'
END || UPPER(SUBSTRING(REPLACE(id, '-', ''), 1, 12));

ALTER TABLE platform_accounts
    ALTER COLUMN public_id SET NOT NULL;

CREATE UNIQUE INDEX uq_platform_accounts_public_id
    ON platform_accounts (public_id);
