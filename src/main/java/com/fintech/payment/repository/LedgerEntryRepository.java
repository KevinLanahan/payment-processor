package com.fintech.payment.repository;

import com.fintech.payment.model.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByTransactionId(UUID transactionId);

    Page<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    /**
     * Recomputes the ledger balance for a single account.
     *
     * CREDIT entries add to the balance; DEBIT entries subtract.
     * This is the core query used by reconciliation.
     *
     * The COALESCE handles accounts that have no ledger entries yet (balance = 0).
     *
     * Note: JPQL enum comparisons use the fully-qualified enum constant reference,
     * not a string literal. This is portable across JPA providers.
     */
    @Query("""
        SELECT COALESCE(
            SUM(CASE WHEN e.entryType = com.fintech.payment.enums.EntryType.CREDIT
                     THEN e.amount
                     ELSE -e.amount END),
            0
        )
        FROM LedgerEntry e
        WHERE e.account.id = :accountId
        """)
    BigDecimal computeLedgerBalanceForAccount(@Param("accountId") UUID accountId);

    /**
     * Returns the count of entries for a given transaction.
     * A healthy COMPLETED transaction must have exactly 2.
     */
    long countByTransactionId(UUID transactionId);
}
