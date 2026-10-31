ALTER TABLE categories
    ADD COLUMN created_by_id BIGINT REFERENCES app_users(id);

ALTER TABLE categories
    ADD COLUMN updated_by_id BIGINT REFERENCES app_users(id);

ALTER TABLE categories
    ADD COLUMN deleted_by_id BIGINT REFERENCES app_users(id);

ALTER TABLE categories
    ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE questions
    ALTER COLUMN category_id DROP NOT NULL;

ALTER TABLE questions
    ADD COLUMN global_pool BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX ix_questions_global_pool ON questions(global_pool, difficulty, active);

UPDATE categories SET question_count = 16 WHERE question_count = 40;

ALTER TABLE questions
    ADD CONSTRAINT ck_question_difficulty CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD'));
