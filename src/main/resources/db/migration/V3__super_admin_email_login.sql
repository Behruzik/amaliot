ALTER TABLE app_users ALTER COLUMN phone DROP NOT NULL;
ALTER TABLE app_users ADD COLUMN email VARCHAR(254);
ALTER TABLE app_users ADD CONSTRAINT uq_app_users_email UNIQUE (email);
