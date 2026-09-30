--liquibase formatted sql

--changeset genlink:0039-fix-media-checksum-type
-- 0038 created checksum_sha256 as CHAR(64), which Postgres stores as bpchar
-- (JDBC type CHAR). The JPA entity maps the String to VARCHAR, so Hibernate
-- schema-validation rejected the mismatch (CHAR vs VARCHAR). Widen to VARCHAR(64)
-- to match the entity.
--
-- Forward-only on purpose: 0038 is already applied on the dev database, so we
-- alter here rather than editing an applied changeset (which would fail
-- Liquibase's checksum check). Fresh installs run 0038 then this, ending correct.
ALTER TABLE media_files ALTER COLUMN checksum_sha256 TYPE VARCHAR(64);

--rollback ALTER TABLE media_files ALTER COLUMN checksum_sha256 TYPE CHAR(64);
