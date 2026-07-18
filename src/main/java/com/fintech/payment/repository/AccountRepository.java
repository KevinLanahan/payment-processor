package com.fintech.payment.repository;

import com.fintech.payment.model.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * Acquires a PESSIMISTIC_WRITE (SELECT ... FOR UPDATE) lock on the row.
     *
     * Used by TransactionService when debiting / crediting balances to prevent
     * the TOCTOU race where two concurrent transfers both read the same balance,
     * both pass the "sufficient funds" check, and both proceed — resulting in a
     * negative balance. The lock is held until the enclosing @Transactional
     * method commits or rolls back.
     *
     * Lock ordering: always lock the lower UUID first to prevent deadlocks when
     * two concurrent transfers touch the same pair of accounts in opposite order.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithLock(@Param("id") UUID id);

    boolean existsByAccountNumber(String accountNumber);
}
