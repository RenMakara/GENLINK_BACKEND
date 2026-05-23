-- Update users table for Google Auth
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE users ADD COLUMN google_id VARCHAR(255);
ALTER TABLE users ADD COLUMN avatar_url TEXT;
ALTER TABLE users ADD COLUMN auth_provider VARCHAR(50) DEFAULT 'LOCAL' NOT NULL;
