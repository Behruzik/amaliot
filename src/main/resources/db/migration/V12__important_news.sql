ALTER TABLE news ADD COLUMN important BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE news SET important = TRUE WHERE news_type IN ('NEW_EXAM', 'NEW_CERTIFICATE');
CREATE INDEX ix_news_important_category_created ON news(important, category_id, created_at);
