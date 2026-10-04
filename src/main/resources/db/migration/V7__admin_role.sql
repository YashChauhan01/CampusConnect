-- Administrators manage campus zones. Granted at login to the emails listed in ADMIN_EMAILS.
ALTER TABLE students ADD COLUMN admin BOOLEAN NOT NULL DEFAULT FALSE;
