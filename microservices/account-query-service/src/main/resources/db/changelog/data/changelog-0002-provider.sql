-- liquibase formatted sql

-- changeset makar:1773524359085-3
INSERT INTO branches (branch_id, name, is_opening)
VALUES
    ('a1b2c3d4-e5f6-7890-ab12-cd34ef567890', 'Main Branch', true),
    ('b2c3d4e5-f6a7-8901-bc23-de45fa678901', 'Downtown Branch', true),
    ('c3d4e5f6-a7b8-9012-cd34-ef56ab789012', 'Westside Branch', false),
    ('d4e5f6a7-b8c9-0123-de45-fa67bc890123', 'Airport Branch', true),
    ('e5f6a7b8-c9d0-1234-ef56-ab78cd901234', 'Suburban Branch', true);

-- changeset makar:1773524359085-4
INSERT INTO customers (customer_id, customer_name, phone_number)
VALUES
    ('f6a7b8c9-d0e1-2345-fa67-bc89de012345', 'John Doe', '+1-555-0101'),
    ('a7b8c9d0-e1f2-3456-ab78-cd90ef123456', 'Jane Smith', '+1-555-0102'),
    ('b8c9d0e1-f2a3-4567-bc89-de01fa234567', 'Michael Johnson', '+1-555-0103'),
    ('c9d0e1f2-a3b4-5678-cd90-ef12ab345678', 'Emily Davis', '+1-555-0104'),
    ('d0e1f2a3-b4c5-6789-de01-fa23bc456789', 'David Wilson', '+1-555-0105'),
    ('e1f2a3b4-c5d6-7890-ef12-ab34cd567890', 'Sarah Brown', '+1-555-0106'),
    ('f2a3b4c5-d6e7-8901-fa23-bc45de678901', 'James Martinez', '+1-555-0107'),
    ('a3b4c5d6-e7f8-9012-ab34-cd56ef789012', 'Lisa Anderson', '+1-555-0108');