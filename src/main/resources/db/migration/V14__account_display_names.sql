ALTER TABLE platform_accounts
    ADD COLUMN display_name VARCHAR(160);

UPDATE platform_accounts account
SET display_name = profile.full_name
FROM patient_profiles profile
WHERE profile.account_id = account.id;
