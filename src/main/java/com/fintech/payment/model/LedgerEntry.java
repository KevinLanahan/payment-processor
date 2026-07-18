package com.fintech.payment.model;

import com.fintech.payment.enums.EntryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An immutable record of money movement on an account.
 *
 * Double-entry invariant enforced at the application layer:
 *   For every Transaction, two LedgerEntry rows are created atomically:
 *     1. DEBIT  on sourceAccount  (balance decreases)
 *     2. CREDIT on destAccount    (balance increases)
 *
 *   The amounts of both entries must be equal.
 *
 * Rows in this table are NEVER updated or deleted. They form the
 * immutable audit trail used by the reconciliation service.
 */
@Entity
@Table(name = "ledger_entries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10, updatable = false)
    private EntryType entryType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    /**
     * Snapshot of the account balance immediately after this entry was posted.
     * Used by the reconciliation service to detect gaps or re-ordering in the
     * ledger timeline.
     */
    @Column(name = "running_balance", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal runningBalance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
