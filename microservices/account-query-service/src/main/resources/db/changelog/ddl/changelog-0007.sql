-- liquibase formatted sql

-- changeset makar:1773524359085-1
CREATE TABLE branches
(
    branch_id  UUID NOT NULL,
    name       VARCHAR(255),
    is_opening BOOLEAN,
    CONSTRAINT pk_branches PRIMARY KEY (branch_id)
);

-- changeset makar:1773524359085-2
CREATE TABLE customers
(
    customer_id   UUID NOT NULL,
    customer_name VARCHAR(255),
    phone_number  VARCHAR(255),
    CONSTRAINT pk_customers PRIMARY KEY (customer_id)
);

