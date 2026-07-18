-- Idempotency key store
-- Persists the result of the first successful request so retries return
-- an identical response without re-executing business logic.
CREATE TABLE idempotency_keys (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(255)    NOT NULL,
    endpoint        VARCHAR(255)    NOT NULL,        -- e.g. POST /api/v1/transactions
    request_hash    VARCHAR(64)     NOT NULL,        -- SHA-256 of request body — detects payload mismatch on retry
    response_status INT             NOT NULL,
    response_body   TEXT            NOT NULL,
    expires_at      TIMESTAMPTZ     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_idempotency_keys PRIMARY KEY (id),
    CONSTRAINT uq_idempotency_keys_key_endpoint UNIQUE (idempotency_key, endpoint)
);

CREATE INDEX idx_idempotency_keys_key         ON idempotency_keys (idempotency_key);
CREATE INDEX idx_idempotency_keys_expires_at  ON idempotency_keys (expires_at);

-- Periodic cleanup job can delete rows WHERE expires_at < NOW()
COMMENT ON TABLE idempotency_keys IS 'Stores serialized HTTP responses keyed by idempotency_key + endpoint. TTL-expired rows are safe to purge.';
COMMENT ON COLUMN idempotency_keys.request_hash IS 'Hash of the original request body. If a retry sends the same key but different body, the request is rejected (409).';
