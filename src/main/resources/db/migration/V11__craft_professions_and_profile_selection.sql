ALTER TABLE app_users ADD COLUMN profession_category_id BIGINT REFERENCES categories(id);

INSERT INTO categories (name, description, active, duration_minutes, question_count, additional_question_count, retake_delay_hours, created_at, updated_at)
SELECT 'Elektrik ustasi', 'Elektr montaj va xavfsiz ishlash bo‘yicha kasbiy testlar', TRUE, 60, 40, 0, 24, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Elektrik ustasi');

INSERT INTO categories (name, description, active, duration_minutes, question_count, additional_question_count, retake_delay_hours, created_at, updated_at)
SELECT 'Santexnik', 'Suv ta’minoti va sanitariya tizimlari bo‘yicha kasbiy testlar', TRUE, 60, 40, 0, 24, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Santexnik');

INSERT INTO categories (name, description, active, duration_minutes, question_count, additional_question_count, retake_delay_hours, created_at, updated_at)
SELECT 'Payvandchi', 'Payvandlash usullari va xavfsizlik qoidalari bo‘yicha kasbiy testlar', TRUE, 60, 40, 0, 24, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Payvandchi');

INSERT INTO categories (name, description, active, duration_minutes, question_count, additional_question_count, retake_delay_hours, created_at, updated_at)
SELECT 'Duradgor', 'Yog‘ochga ishlov berish va duradgorlik bo‘yicha kasbiy testlar', TRUE, 60, 40, 0, 24, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Duradgor');

INSERT INTO categories (name, description, active, duration_minutes, question_count, additional_question_count, retake_delay_hours, created_at, updated_at)
SELECT 'G‘isht teruvchi', 'G‘isht terish va devor qurish bo‘yicha kasbiy testlar', TRUE, 60, 40, 0, 24, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'G‘isht teruvchi');

INSERT INTO categories (name, description, active, duration_minutes, question_count, additional_question_count, retake_delay_hours, created_at, updated_at)
SELECT 'Suvoqchi', 'Suvoq ishlari va sirtlarni tayyorlash bo‘yicha kasbiy testlar', TRUE, 60, 40, 0, 24, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Suvoqchi');

INSERT INTO categories (name, description, active, duration_minutes, question_count, additional_question_count, retake_delay_hours, created_at, updated_at)
SELECT 'Bo‘yoqchi', 'Bo‘yash va pardozlash ishlari bo‘yicha kasbiy testlar', TRUE, 60, 40, 0, 24, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Bo‘yoqchi');

INSERT INTO categories (name, description, active, duration_minutes, question_count, additional_question_count, retake_delay_hours, created_at, updated_at)
SELECT 'Kafel teruvchi', 'Kafel va plitka yotqizish bo‘yicha kasbiy testlar', TRUE, 60, 40, 0, 24, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Kafel teruvchi');

INSERT INTO categories (name, description, active, duration_minutes, question_count, additional_question_count, retake_delay_hours, created_at, updated_at)
SELECT 'Tom yopuvchi', 'Tom konstruksiyalari va tom yopish materiallari bo‘yicha kasbiy testlar', TRUE, 60, 40, 0, 24, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Tom yopuvchi');

INSERT INTO categories (name, description, active, duration_minutes, question_count, additional_question_count, retake_delay_hours, created_at, updated_at)
SELECT 'Gipsokarton ustasi', 'Gipsokarton konstruksiyalarini montaj qilish bo‘yicha kasbiy testlar', TRUE, 60, 40, 0, 24, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Gipsokarton ustasi');
