--liquibase formatted sql

--changeset genlink:0035-add-lesson-related-category
-- Phase 4 (insights): let a lesson be matched to a spending category, so a
-- "you overspent on FOOD" insight can point straight at the relevant lesson.
-- Nullable and free-form (expense categories are free-form strings, not an enum);
-- an insight falls back to a topic-matched lesson when no category match exists.
ALTER TABLE financial_lessons ADD COLUMN IF NOT EXISTS related_category VARCHAR(80);

-- One demo mapping so a category-spike insight resolves to a specific lesson out of the box.
-- FOOD is the most common student overspend category; point it at the smart-spending lesson.
UPDATE financial_lessons SET related_category = 'FOOD'
    WHERE lesson_id = 'a0000000-0000-4000-8000-000000000006';

--rollback ALTER TABLE financial_lessons DROP COLUMN IF EXISTS related_category;
