--liquibase formatted sql

--changeset renmakara:0005-create-security-audit-logs-table
CREATE TABLE IF NOT EXISTS security_audit_logs (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    ip_address VARCHAR(45),
    device_id VARCHAR(255),
    details TEXT,
    status VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'CampusLinkKH',
    updated_at TIMESTAMP,
    updated_by VARCHAR(255)
);

--rollback DROP TABLE IF EXISTS security_audit_logs;
