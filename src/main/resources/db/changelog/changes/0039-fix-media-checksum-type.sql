--liquibase formatted sql

--changeset genlink:0039-fix-media-checksum-type
-- 0038 created checksum_sha256 as CHAR(64), which Postgres stores as bpchar (JDBC type CHAR).
-- Liquibase's checksum check). Fresh installs run 0038 then this, ending correct.
ALTER TABLE media_files ALTER COLUMN checksum_sha256 TYPE VARCHAR(64);

--rollback ALTER TABLE media_files ALTER COLUMN checksum_sha256 TYPE CHAR(64);
