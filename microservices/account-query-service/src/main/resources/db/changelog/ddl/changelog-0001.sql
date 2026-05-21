-- liquibase formatted sql

-- changeset makara:1773031264689-1
CREATE TABLE account_types
(
    account_type_id   UUID NOT NULL,
    account_type_code VARCHAR(255),
    CONSTRAINT pk_account_type PRIMARY KEY (account_type_id)
);

-- changeset makara:1773031264689-2
CREATE TABLE accounts
(
    account_id        UUID NOT NULL,
    customer_id       UUID,
    branch_id         UUID,
    account_number    VARCHAR(255),
    account_holder    VARCHAR(255),
    account_type_code VARCHAR(255),
    account_status    VARCHAR(255),
    account_type_id   UUID NOT NULL,
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    created_at        TIMESTAMP WITHOUT TIME ZONE,
    updated_at        TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_accounts PRIMARY KEY (account_id)
);

-- changeset makara:1773031264689-3
ALTER TABLE accounts
    ADD CONSTRAINT FK_ACCOUNTS_ON_ACCOUNT_TYPE FOREIGN KEY (account_type_id) REFERENCES account_types (account_type_id);
