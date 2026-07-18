-- Transactions table
-- A transaction represents a single payment event. It spawns exactly two
-- ledger entries (debit + credit), enforcing the double-entry invariant.
CREATE TABLE transactions (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    idempotency_key     VARCHAR(255)    NOT NULL,
    source_account_id   UUID            NOT NULL,
    dest_account_id     UUID            NOT NULL,
    amount              NUMERIC(19, 4)  NOT NULL,
    currency            VARCHAR(3)      NOT NULL DEFAULT 'USD',
    type                VARCHAR(30)     NOT NULL,      -- TRANSFER, DEPOSIT, WITHDRAWAL
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    failure_reason      TEXT,
    reference           VARCHAR(255),                 -- optional human-readable memo
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_transactions PRIMARY KEY (id),
    CONSTRAINT uq_transactions_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_transactions_source FOREIGN KEY (source_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_transactions_dest   FOREIGN KEY (dest_account_id)   REFERENCES accounts (id),
    CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_transactions_different_accounts CHECK (source_account_id <> dest_account_id),
    CONSTRAINT chk_transactions_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REVERSED')),
    CONSTRAINT chk_transactions_type   CHECK (type   IN ('TRANSFER', 'DEPOSIT', 'WITHDRAWAL'))
);

CREATE INDEX idx_transactions_idempotency_key  ON transactions (idempotency_key);
CREATE INDEX idx_transactions_source_account   ON transactions (source_account_id);
CREATE INDEX idx_transactions_dest_account     ON transactions (dest_account_id);
CREATE INDEX idx_transactions_status           ON transactions (status);
CREATE INDEX idx_transactions_created_at       ON transactions (created_at DESC);

COMMENT ON TABLE transactions IS 'Payment events. Each completed transaction must have exactly two corresponding ledger_entries rows.';
COMMENT ON COLUMN transactions.idempotency_key IS 'Client-supplied key. Duplicate submissions with the same key return the original result without reprocessing.';
