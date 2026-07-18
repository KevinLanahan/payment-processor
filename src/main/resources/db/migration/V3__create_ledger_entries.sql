-- Ledger entries table — the immutable audit log of all money movement.
-- Every transaction generates exactly one DEBIT and one CREDIT entry.
-- The fundamental double-entry invariant:
--   SUM(amount WHERE entry_type = 'CREDIT') - SUM(amount WHERE entry_type = 'DEBIT')
--   = current balance  (for any given account)
--
-- Ledger entries are NEVER updated or deleted. They are the source of truth.
CREATE TABLE ledger_entries (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    transaction_id  UUID            NOT NULL,
    account_id      UUID            NOT NULL,
    entry_type      VARCHAR(10)     NOT NULL,       -- DEBIT or CREDIT
    amount          NUMERIC(19, 4)  NOT NULL,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'USD',
    running_balance NUMERIC(19, 4)  NOT NULL,       -- account balance AFTER this entry
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_ledger_entries PRIMARY KEY (id),
    CONSTRAINT fk_ledger_entries_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id),
    CONSTRAINT fk_ledger_entries_account     FOREIGN KEY (account_id)     REFERENCES accounts (id),
    CONSTRAINT chk_ledger_entries_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_ledger_entries_type CHECK (entry_type IN ('DEBIT', 'CREDIT'))
);

-- Lookups by transaction (to verify both sides of a journal entry exist)
CREATE INDEX idx_ledger_entries_transaction_id ON ledger_entries (transaction_id);
-- Lookups by account for statement/history queries
CREATE INDEX idx_ledger_entries_account_id ON ledger_entries (account_id, created_at DESC);

COMMENT ON TABLE ledger_entries IS 'Immutable double-entry ledger. Every row is permanent — reconciliation reads this table to validate account balances.';
COMMENT ON COLUMN ledger_entries.running_balance IS 'Balance of the account immediately after this entry was posted. Snapshot for auditing; do not use for current balance.';
