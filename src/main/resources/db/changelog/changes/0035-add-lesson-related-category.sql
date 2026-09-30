--liquibase formatted sql

--changeset genlink:0035-add-lesson-related-category
-- Links lessons to spending categories for relevant insights.
ALTER TABLE financial_lessons ADD COLUMN IF NOT EXISTS related_category VARCHAR(80);

-- One demo mapping so a category-spike insight resolves to a specific lesson out of the box.
UPDATE financial_lessons SET related_category = 'FOOD'
    WHERE lesson_id = 'a0000000-0000-4000-8000-000000000006';

--rollback ALTER TABLE financial_lessons DROP COLUMN IF EXISTS related_category;
