ALTER TABLE app_users ADD COLUMN active_since TIMESTAMP WITH TIME ZONE;
ALTER TABLE app_users ADD COLUMN active_seconds_total BIGINT NOT NULL DEFAULT 0;
UPDATE app_users SET active_since = created_at WHERE active = TRUE;
