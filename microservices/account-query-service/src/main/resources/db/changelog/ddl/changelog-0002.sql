-- liquibase formatted sql

-- changeset makar:1773031375725-1
CREATE SEQUENCE IF NOT EXISTS association_value_entry_seq START WITH 1 INCREMENT BY 50;

-- changeset makar:1773031375725-2
CREATE SEQUENCE IF NOT EXISTS domain_event_entry_seq START WITH 1 INCREMENT BY 50;

-- changeset makar:1773031375725-3
CREATE TABLE association_value_entry
(
    id                BIGINT NOT NULL,
    saga_id           VARCHAR(255),
    association_key   VARCHAR(255),
    association_value VARCHAR(255),
    saga_type         VARCHAR(255),
    CONSTRAINT pk_associationvalueentry PRIMARY KEY (id)
);

-- changeset makar:1773031375725-4
CREATE TABLE dead_letter_entry
(
    dead_letter_id       VARCHAR(255) NOT NULL,
    processing_group     VARCHAR(255),
    sequence_identifier  VARCHAR(255),
    sequence_index       BIGINT       NOT NULL,
    enqueued_at          TIMESTAMP WITHOUT TIME ZONE,
    last_touched         TIMESTAMP WITHOUT TIME ZONE,
    processing_started   TIMESTAMP WITHOUT TIME ZONE,
    cause_type           VARCHAR(255),
    cause_message        VARCHAR(1023),
    diagnostics          OID,
    message_type         VARCHAR(255),
    event_identifier     VARCHAR(255) NOT NULL,
    time_stamp           VARCHAR(255),
    payload_type         VARCHAR(255),
    payload_revision     VARCHAR(255),
    payload              OID,
    meta_data            OID,
    type                 VARCHAR(255),
    aggregate_identifier VARCHAR(255),
    sequence_number      BIGINT,
    token_type           VARCHAR(255),
    token                OID,
    CONSTRAINT pk_deadletterentry PRIMARY KEY (dead_letter_id)
);

-- changeset makar:1773031375725-5
CREATE TABLE domain_event_entry
(
    global_index         BIGINT       NOT NULL,
    type                 VARCHAR(255),
    aggregate_identifier VARCHAR(255),
    sequence_number      BIGINT       NOT NULL,
    event_identifier     VARCHAR(255) NOT NULL,
    time_stamp           VARCHAR(255),
    payload_type         VARCHAR(255),
    payload_revision     VARCHAR(255),
    payload              OID,
    meta_data            OID,
    CONSTRAINT pk_domainevententry PRIMARY KEY (global_index)
);

-- changeset makar:1773031375725-6
CREATE TABLE saga_entry
(
    saga_id         VARCHAR(255) NOT NULL,
    saga_type       VARCHAR(255),
    revision        VARCHAR(255),
    serialized_saga OID,
    CONSTRAINT pk_sagaentry PRIMARY KEY (saga_id)
);

-- changeset makar:1773031375725-7
CREATE TABLE snapshot_event_entry
(
    aggregate_identifier VARCHAR(255) NOT NULL,
    sequence_number      BIGINT       NOT NULL,
    type                 VARCHAR(255) NOT NULL,
    event_identifier     VARCHAR(255) NOT NULL,
    time_stamp           VARCHAR(255),
    payload_type         VARCHAR(255),
    payload_revision     VARCHAR(255),
    payload              OID,
    meta_data            OID,
    CONSTRAINT pk_snapshotevententry PRIMARY KEY (aggregate_identifier, sequence_number, type)
);

-- changeset makar:1773031375725-8
CREATE TABLE token_entry
(
    processor_name VARCHAR(255) NOT NULL,
    token          OID,
    token_type     VARCHAR(255),
    timestamp      VARCHAR(255),
    owner          VARCHAR(255),
    segment        INTEGER      NOT NULL,
    CONSTRAINT pk_tokenentry PRIMARY KEY (processor_name, segment)
);

-- changeset makar:1773031375725-9
ALTER TABLE domain_event_entry
    ADD CONSTRAINT uc_domainevententry_eventidentifier UNIQUE (event_identifier);

-- changeset makar:1773031375725-10
ALTER TABLE snapshot_event_entry
    ADD CONSTRAINT uc_snapshotevententry_eventidentifier UNIQUE (event_identifier);
