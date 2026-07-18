package com.fintech.payment.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Persisted record of a completed request, keyed by the client's
 * idempotency token. If the same key arrives again before expiry,
 * the stored response is returned without re-executing any logic.
 *
 * Key design decisions:
 *   - requestHash: SHA-256 of the request body. If a retry sends the same
 *     key with a different body, we reject it (409 Conflict) because that
 *     indicates a client bug — the key should uniquely identify one intent.
 *   - Rows expire after a configurable TTL (default 24 h). A scheduled job
 *     deletes expired rows so the table doesn't grow unboundedly.
 */
@Entity
@Table(name = "idempotency_keys",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_idempotency_keys_key_endpoint",
           columnNames = {"idempotency_key", "endpoint"}
       ))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "endpoint", nullable = false, length = 255)
    private String endpoint;

    /** SHA-256 hex digest of the original request body. */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Column(name = "response_body", nullable = false, columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public boolean isExpired() {
        return OffsetDateTime.now().isAfter(expiresAt);
    }
}
