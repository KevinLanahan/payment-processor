-- Accounts table
-- Uses NUMERIC for money — never FLOAT for financial amounts
CREATE TABLE accounts (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    account_number  VARCHAR(20)     NOT NULL,
    owner_name      VARCHAR(255)    NOT NULL,
    balance         NUMERIC(19, 4)  NOT NULL DEFAULT 0.0000,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'USD',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    version         BIGINT          NOT NULL DEFAULT 0,   -- optimistic lock version
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT uq_accounts_account_number UNIQUE (account_number),
    CONSTRAINT chk_accounts_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT chk_accounts_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_accounts_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'))
);

CREATE INDEX idx_accounts_account_number ON accounts (account_number);
CREATE INDEX idx_accounts_status ON accounts (status);

COMMENT ON TABLE accounts IS 'Customer accounts holding balances. Balance is the authoritative figure; reconciliation validates it against ledger entries.';
COMMENT ON COLUMN accounts.version IS 'JPA optimistic locking — incremented on every update to prevent lost updates under concurrent writes.';
COMMENT ON COLUMN accounts.balance IS 'Denormalized running balance. Must always equal sum of ledger CREDIT entries minus sum of DEBIT entries for this account.';
