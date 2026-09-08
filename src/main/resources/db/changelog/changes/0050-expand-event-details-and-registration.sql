--liquibase formatted sql

--changeset genlink:0050-expand-event-details-and-registration
-- date_time remains the event start column for compatibility with existing BFF queries.
ALTER TABLE events ADD COLUMN IF NOT EXISTS end_date_time TIMESTAMP;
UPDATE events
SET end_date_time = date_time + INTERVAL '1 hour'
WHERE end_date_time IS NULL;
ALTER TABLE events ALTER COLUMN end_date_time SET NOT NULL;

ALTER TABLE events ADD COLUMN IF NOT EXISTS event_mode VARCHAR(20) NOT NULL DEFAULT 'IN_PERSON';
ALTER TABLE events ADD COLUMN IF NOT EXISTS meeting_url TEXT;

ALTER TABLE event_rsvps ADD COLUMN IF NOT EXISTS full_name VARCHAR(180);
ALTER TABLE event_rsvps ADD COLUMN IF NOT EXISTS email VARCHAR(320);
ALTER TABLE event_rsvps ADD COLUMN IF NOT EXISTS phone_number VARCHAR(32);
ALTER TABLE event_rsvps ADD COLUMN IF NOT EXISTS university VARCHAR(180);
ALTER TABLE event_rsvps ADD COLUMN IF NOT EXISTS faculty VARCHAR(180);
ALTER TABLE event_rsvps ADD COLUMN IF NOT EXISTS study_year VARCHAR(80);
ALTER TABLE event_rsvps ADD COLUMN IF NOT EXISTS terms_accepted BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE events ADD CONSTRAINT chk_events_end_after_start
    CHECK (end_date_time > date_time);
ALTER TABLE events ADD CONSTRAINT chk_events_coordinates_pair
    CHECK ((latitude IS NULL AND longitude IS NULL) OR (latitude IS NOT NULL AND longitude IS NOT NULL));

--rollback ALTER TABLE events DROP CONSTRAINT IF EXISTS chk_events_coordinates_pair; ALTER TABLE events DROP CONSTRAINT IF EXISTS chk_events_end_after_start; ALTER TABLE event_rsvps DROP COLUMN IF EXISTS terms_accepted; ALTER TABLE event_rsvps DROP COLUMN IF EXISTS study_year; ALTER TABLE event_rsvps DROP COLUMN IF EXISTS faculty; ALTER TABLE event_rsvps DROP COLUMN IF EXISTS university; ALTER TABLE event_rsvps DROP COLUMN IF EXISTS phone_number; ALTER TABLE event_rsvps DROP COLUMN IF EXISTS email; ALTER TABLE event_rsvps DROP COLUMN IF EXISTS full_name; ALTER TABLE events DROP COLUMN IF EXISTS meeting_url; ALTER TABLE events DROP COLUMN IF EXISTS event_mode; ALTER TABLE events DROP COLUMN IF EXISTS end_date_time;
