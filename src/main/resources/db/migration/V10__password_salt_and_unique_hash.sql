ALTER TABLE app_users ADD COLUMN password_salt VARCHAR(18);

-- Password hashes are generated with a per-account salt. The unique index
-- guarantees that no two accounts can store the same 18-character verifier.
CREATE UNIQUE INDEX uq_app_users_password_hash ON app_users(password_hash);
