package com.fintech.payment.model;

import com.fintech.payment.enums.AccountStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A customer account that holds a monetary balance.
 *
 * The {@code balance} field is a denormalized snapshot: it must always equal
 * the net of all CREDIT minus DEBIT ledger entries for this account.
 * Reconciliation validates this invariant periodically.
 *
 * {@code @Version} enables JPA optimistic locking — Hibernate will include
 * the current version in every UPDATE WHERE clause and throw
 * {@link jakarta.persistence.OptimisticLockException} if another transaction
 * has already incremented it. This prevents lost-update anomalies without
 * holding a long-lived DB lock.
 */
@Entity
@Table(name = "accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @Column(name = "owner_name", nullable = false)
    private String ownerName;

    /**
     * Current balance. Always maintained in sync with ledger entries.
     * Use NUMERIC(19,4) in DB — never store money in floating-point types.
     */
    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    /**
     * JPA optimistic lock version. Hibernate increments this on every UPDATE.
     * A stale read from a concurrent transaction will fail fast rather than
     * silently overwriting the other writer's changes.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        if (status == null) status = AccountStatus.ACTIVE;
        if (balance == null) balance = BigDecimal.ZERO;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public boolean isActive() {
        return AccountStatus.ACTIVE.equals(status);
    }
}
