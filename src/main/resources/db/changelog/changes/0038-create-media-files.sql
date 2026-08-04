--liquibase formatted sql

--changeset genlink:0038-create-media-files
-- Metadata for user-uploaded media. The bytes live in S3-compatible object
-- storage (DigitalOcean Spaces); this table only records what/where/whose, so
-- the database stays small and backups stay fast.
--
-- Three timestamps track the lifecycle: created_at (presign requested),
-- uploaded_at (object's LastModified, read at confirm), confirmed_at (confirm
-- processed). deleted_at is a soft delete; a later purge job removes the object.
CREATE TABLE media_files (
    id              UUID PRIMARY KEY,
    owner_id        UUID NOT NULL REFERENCES users(user_id),
    storage_key     TEXT NOT NULL UNIQUE,
    public_url      TEXT,
    content_type    VARCHAR(100) NOT NULL,
    size_bytes      BIGINT NOT NULL,
    checksum_sha256 CHAR(64),
    width           INTEGER,
    height          INTEGER,
    duration_ms     BIGINT,
    purpose         VARCHAR(40) NOT NULL,
    visibility      VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    uploaded_at     TIMESTAMP,
    confirmed_at    TIMESTAMP,
    deleted_at      TIMESTAMP
);

-- Active-media lookups skip soft-deleted rows.
CREATE INDEX idx_media_owner ON media_files (owner_id) WHERE deleted_at IS NULL;
-- Orphan cleanup: PENDING rows that were presigned but never confirmed.
CREATE INDEX idx_media_status_created ON media_files (status, created_at);
-- Duplicate detection: same owner uploading identical bytes.
CREATE INDEX idx_media_checksum ON media_files (owner_id, checksum_sha256);
-- Purge job: soft-deleted rows past the grace period.
CREATE INDEX idx_media_deleted ON media_files (deleted_at) WHERE deleted_at IS NOT NULL;

--rollback DROP TABLE media_files;
