

------------------------------------------------------------
-- CUSTOMER SEGMENT
------------------------------------------------------------
INSERT INTO CUSTOMER_SEGMENT (CODE, NAME, STATUS)
VALUES ('RETAIL', 'Retail Customer', 'ACTIVE');

INSERT INTO CUSTOMER_SEGMENT (CODE, NAME, STATUS)
VALUES ('CORP', 'Corporate Customer', 'ACTIVE');

INSERT INTO CUSTOMER_SEGMENT (CODE, NAME, STATUS)
VALUES ('VIP', 'VIP Customer', 'ACTIVE');

INSERT INTO CUSTOMER_SEGMENT (CODE, NAME, STATUS)
VALUES ('STUDENT', 'Student Customer', 'ACTIVE');

------------------------------------------------------------
-- CUSTOMER
------------------------------------------------------------
INSERT INTO CUSTOMER (
    CUSTOMER_NUMBER, FIRST_NAME, LAST_NAME, DATE_OF_BIRTH, EMAIL, SEGMENT_ID,
    ADDRESS_XML, CONTACT_XML, KYC_XML, PROFITABILITY, STATUS, CREATED_BY
) VALUES (
    'CUST0001', 'Sokha', 'Chan', DATE '1990-05-12', 'sokha.chan@example.com', 1,
    XMLTYPE('<Addresses><Address><Line1>123 Main St</Line1><City>Phnom Penh</City><Country>Cambodia</Country><Zip>12000</Zip></Address></Addresses>'),
    XMLTYPE('<Contacts><Contact><Type>MOBILE</Type><Number>+85512345678</Number></Contact></Contacts>'),
    XMLTYPE('<KYC><Document><Type>PASSPORT</Type><Number>PA1234567</Number></Document></KYC>'),
    1000, 'ACTIVE', 'SYSTEM'
);

INSERT INTO CUSTOMER (
    CUSTOMER_NUMBER, FIRST_NAME, LAST_NAME, DATE_OF_BIRTH, EMAIL, SEGMENT_ID,
    ADDRESS_XML, CONTACT_XML, KYC_XML, PROFITABILITY, STATUS, CREATED_BY
) VALUES (
    'CUST0002', 'Sopheap', 'Lim', DATE '1985-08-20', 'sopheap.lim@example.com', 2,
    XMLTYPE('<Addresses><Address><Line1>456 Riverside Rd</Line1><City>Siem Reap</City><Country>Cambodia</Country><Zip>17000</Zip></Address></Addresses>'),
    XMLTYPE('<Contacts><Contact><Type>HOME</Type><Number>+85523456789</Number></Contact></Contacts>'),
    XMLTYPE('<KYC><Document><Type>ID</Type><Number>ID987654</Number></Document></KYC>'),
    5000, 'ACTIVE', 'SYSTEM'
);

INSERT INTO CUSTOMER (
    CUSTOMER_NUMBER, FIRST_NAME, LAST_NAME, DATE_OF_BIRTH, EMAIL, SEGMENT_ID,
    ADDRESS_XML, CONTACT_XML, KYC_XML, PROFITABILITY, STATUS, CREATED_BY
) VALUES (
    'CUST0003', 'Ratanak', 'Meas', DATE '1995-03-15', 'ratanak.meas@example.com', 3,
    XMLTYPE('<Addresses><Address><Line1>789 Riverside Blvd</Line1><City>Battambang</City><Country>Cambodia</Country><Zip>02000</Zip></Address></Addresses>'),
    XMLTYPE('<Contacts><Contact><Type>MOBILE</Type><Number>+85534567890</Number></Contact></Contacts>'),
    XMLTYPE('<KYC><Document><Type>ID</Type><Number>ID123456</Number></Document></KYC>'),
    2000, 'ACTIVE', 'SYSTEM'
);

INSERT INTO CUSTOMER (
    CUSTOMER_NUMBER, FIRST_NAME, LAST_NAME, DATE_OF_BIRTH, EMAIL, SEGMENT_ID,
    ADDRESS_XML, CONTACT_XML, KYC_XML, PROFITABILITY, STATUS, CREATED_BY
) VALUES (
    'CUST0004', 'Srey', 'Sophal', DATE '2002-09-10', 'srey.sophal@example.com', 4,
    XMLTYPE('<Addresses><Address><Line1>321 University Rd</Line1><City>Phnom Penh</City><Country>Cambodia</Country><Zip>12001</Zip></Address></Addresses>'),
    XMLTYPE('<Contacts><Contact><Type>MOBILE</Type><Number>+85545678901</Number></Contact></Contacts>'),
    XMLTYPE('<KYC><Document><Type>STUDENT_ID</Type><Number>STU2025001</Number></Document></KYC>'),
    500, 'ACTIVE', 'SYSTEM'
);

------------------------------------------------------------
-- ACCOUNT TYPE
------------------------------------------------------------
INSERT INTO ACCOUNT_TYPE (TYPE_CODE, DESCRIPTION, CURRENCY, STATUS)
VALUES ('SAVINGS', 'Savings Account', 'USD', 'ACTIVE');

INSERT INTO ACCOUNT_TYPE (TYPE_CODE, DESCRIPTION, CURRENCY, STATUS)
VALUES ('CHECKING', 'Checking Account', 'USD', 'ACTIVE');

INSERT INTO ACCOUNT_TYPE (TYPE_CODE, DESCRIPTION, CURRENCY, STATUS)
VALUES ('LOAN', 'Loan Account', 'USD', 'ACTIVE');

------------------------------------------------------------
-- BRANCH
------------------------------------------------------------
INSERT INTO BRANCH (NAME, LOCATION, STATUS)
VALUES ('Central Branch', 'Phnom Penh', 'ACTIVE');

INSERT INTO BRANCH (NAME, LOCATION, STATUS)
VALUES ('Siem Reap Branch', 'Siem Reap', 'ACTIVE');

INSERT INTO BRANCH (NAME, LOCATION, STATUS)
VALUES ('Battambang Branch', 'Battambang', 'ACTIVE');

------------------------------------------------------------
-- ACCOUNT
------------------------------------------------------------
INSERT INTO ACCOUNT (CUSTOMER_ID, ACCOUNT_NUMBER, TYPE_CODE, BALANCE, BRANCH_ID, STATUS, CREATED_BY)
VALUES (1, 'ACC1001', 'SAVINGS', 1000, 1, 'ACTIVE', 'SYSTEM');

INSERT INTO ACCOUNT (CUSTOMER_ID, ACCOUNT_NUMBER, TYPE_CODE, BALANCE, BRANCH_ID, STATUS, CREATED_BY)
VALUES (2, 'ACC1002', 'CHECKING', 5000, 2, 'ACTIVE', 'SYSTEM');

INSERT INTO ACCOUNT (CUSTOMER_ID, ACCOUNT_NUMBER, TYPE_CODE, BALANCE, BRANCH_ID, STATUS, CREATED_BY)
VALUES (3, 'ACC1003', 'SAVINGS', 2000, 3, 'ACTIVE', 'SYSTEM');

INSERT INTO ACCOUNT (CUSTOMER_ID, ACCOUNT_NUMBER, TYPE_CODE, BALANCE, BRANCH_ID, STATUS, CREATED_BY)
VALUES (1, 'ACC1004', 'LOAN', -500, 1, 'ACTIVE', 'SYSTEM');

INSERT INTO ACCOUNT (CUSTOMER_ID, ACCOUNT_NUMBER, TYPE_CODE, BALANCE, BRANCH_ID, STATUS, CREATED_BY)
VALUES (4, 'ACC1005', 'SAVINGS', 300, 1, 'ACTIVE', 'SYSTEM');
------------------------------------------------------------
-- TRANSACTION TYPE
------------------------------------------------------------
INSERT INTO TRANSACTION_TYPE (TYPE_CODE, DESCRIPTION)
VALUES ('DEPOSIT', 'Deposit Transaction');

INSERT INTO TRANSACTION_TYPE (TYPE_CODE, DESCRIPTION)
VALUES ('WITHDRAWAL', 'Withdrawal Transaction');

INSERT INTO TRANSACTION_TYPE (TYPE_CODE, DESCRIPTION)
VALUES ('TRANSFER', 'Transfer Transaction');

------------------------------------------------------------
-- TRANSACTION
------------------------------------------------------------
INSERT INTO TRANSACTION (ACCOUNT_ID, TYPE_CODE, AMOUNT, CURRENCY, REMARK, CREATED_BY)
VALUES (1, 'DEPOSIT', 500, 'USD', 'Initial deposit', 'SYSTEM');

INSERT INTO TRANSACTION (ACCOUNT_ID, TYPE_CODE, AMOUNT, CURRENCY, REMARK, CREATED_BY)
VALUES (2, 'WITHDRAWAL', 200, 'USD', 'ATM Withdrawal', 'SYSTEM');

INSERT INTO TRANSACTION (ACCOUNT_ID, TYPE_CODE, AMOUNT, CURRENCY, REMARK, CREATED_BY)
VALUES (3, 'DEPOSIT', 1000, 'USD', 'Salary deposit', 'SYSTEM');

INSERT INTO TRANSACTION (ACCOUNT_ID, TYPE_CODE, AMOUNT, CURRENCY, REMARK, CREATED_BY)
VALUES (1, 'TRANSFER', 300, 'USD', 'Transfer to ACC1002', 'SYSTEM');

INSERT INTO TRANSACTION (ACCOUNT_ID, TYPE_CODE, AMOUNT, CURRENCY, REMARK, CREATED_BY)
VALUES (5, 'DEPOSIT', 300, 'USD', 'Scholarship', 'SYSTEM');

COMMIT;