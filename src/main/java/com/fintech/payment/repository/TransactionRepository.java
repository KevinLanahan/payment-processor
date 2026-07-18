package com.fintech.payment.repository;

import com.fintech.payment.enums.TransactionStatus;
import com.fintech.payment.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    @Query("""
        SELECT t FROM Transaction t
        WHERE t.sourceAccount.id = :accountId OR t.destAccount.id = :accountId
        ORDER BY t.createdAt DESC
        """)
    Page<Transaction> findByAccountId(@Param("accountId") UUID accountId, Pageable pageable);

    @Query("""
        SELECT t FROM Transaction t
        WHERE (t.sourceAccount.id = :accountId OR t.destAccount.id = :accountId)
          AND t.status = :status
        ORDER BY t.createdAt DESC
        """)
    Page<Transaction> findByAccountIdAndStatus(
            @Param("accountId") UUID accountId,
            @Param("status") TransactionStatus status,
            Pageable pageable);
}
