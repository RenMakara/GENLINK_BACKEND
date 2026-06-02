--liquibase formatted sql

--changeset renmakara:0001-create-users-table
CREATE TABLE IF NOT EXISTS users (
    user_id UUID PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    username VARCHAR(60) NOT NULL UNIQUE,
    email VARCHAR(180) NOT NULL UNIQUE,
    phone_number VARCHAR(30) UNIQUE,
    password_hash TEXT,
    avatar_url TEXT,
    bio TEXT,
    date_of_birth DATE,
    gender VARCHAR(30),
    university_name VARCHAR(180),
    work_status VARCHAR(50) NOT NULL DEFAULT 'STUDENT',
    location VARCHAR(120),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    account_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    total_xp INTEGER NOT NULL DEFAULT 0,
    level INTEGER NOT NULL DEFAULT 1,
    role VARCHAR(30) NOT NULL DEFAULT 'USER',
    auth_provider VARCHAR(50) NOT NULL DEFAULT 'LOCAL',
    google_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'CampusLinkKH',
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_account_status ON users(account_status);
CREATE INDEX IF NOT EXISTS idx_users_email_verified ON users(email_verified);

--rollback DROP TABLE IF EXISTS users;
