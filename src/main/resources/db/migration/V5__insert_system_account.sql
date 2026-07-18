-- System bank capital account.
-- Acts as the counterpart for all opening-balance deposits.
-- Every new account funded with an initial balance receives a DEPOSIT
-- transaction FROM this account, ensuring full double-entry coverage.
INSERT INTO accounts (id, account_number, owner_name, balance, currency, status, version)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'SYSTEM_BANK_CAPITAL',
    'Bank Capital (System)',
    999999999999.0000,
    'USD',
    'ACTIVE',
    0
);
