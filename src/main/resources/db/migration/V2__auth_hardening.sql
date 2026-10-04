-- Password reset shares the email token table with email verification.
ALTER TABLE email_tokens
    ADD COLUMN purpose VARCHAR(20) NOT NULL DEFAULT 'VERIFY' CHECK (purpose IN ('VERIFY', 'RESET'));

-- Refresh tokens form rotation families so that reuse of a rotated token can revoke the whole chain.
ALTER TABLE refresh_tokens ADD COLUMN family_id UUID;
UPDATE refresh_tokens SET family_id = id;
ALTER TABLE refresh_tokens ALTER COLUMN family_id SET NOT NULL;
CREATE INDEX idx_refresh_family ON refresh_tokens (family_id);

-- Catalog names are unique regardless of letter case.
CREATE UNIQUE INDEX uq_skills_name_ci ON skills (lower(name));
CREATE UNIQUE INDEX uq_subjects_name_ci ON subjects (lower(name));
