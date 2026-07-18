package com.fintech.payment.service;

import com.fintech.payment.dto.CreateTransactionRequest;
import com.fintech.payment.dto.TransactionResponse;
import com.fintech.payment.enums.TransactionStatus;
import com.fintech.payment.exception.AccountNotActiveException;
import com.fintech.payment.exception.AccountNotFoundException;
import com.fintech.payment.exception.InsufficientFundsException;
import com.fintech.payment.model.Account;
import com.fintech.payment.model.Transaction;
import com.fintech.payment.repository.AccountRepository;
import com.fintech.payment.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Core payment processing service.
 *
 * Key design decisions:
 *
 * 1. SERIALIZABLE isolation for fund transfers: prevents phantom reads and
 *    non-repeatable reads. A lower level (e.g. READ_COMMITTED) would allow two
 *    concurrent transfers to both pass the balance check against a stale read.
 *
 * 2. Pessimistic locking via SELECT FOR UPDATE: even under SERIALIZABLE, we
 *    explicitly lock both account rows before reading balances. This turns a
 *    potential serialization failure (retry-able) into a wait instead.
 *
 * 3. Lock ordering by UUID: always acquire account locks in ascending UUID
 *    order. Without this, TX-A locking (src=1, dst=2) and TX-B locking
 *    (src=2, dst=1) creates a deadlock. Consistent ordering eliminates it.
 *
 * 4. @Retryable on transient failures: DB timeouts and lock wait timeouts
 *    are transient — the service retries with exponential backoff before
 *    surfacing an error. Business exceptions (insufficient funds, not found)
 *    propagate immediately without retrying.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final AccountRepository     accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerService         ledgerService;

    /**
     * Creates and processes a payment transaction atomically.
     *
     * The caller is responsible for idempotency key checking BEFORE calling
     * this method (handled in the controller layer via IdempotencyService).
     *
     * @Retryable retries only on transient DB errors — NOT on business exceptions.
     */
    @Retryable(
        retryFor = {TransientDataAccessException.class, jakarta.persistence.PessimisticLockException.class},
        noRetryFor = {InsufficientFundsException.class, AccountNotFoundException.class,
                      AccountNotActiveException.class, IllegalArgumentException.class},
        maxAttemptsExpression = "${payment.retry.max-attempts:3}",
        backoff = @Backoff(
            delayExpression    = "${payment.retry.initial-interval-ms:500}",
            multiplierExpression = "${payment.retry.multiplier:2.0}",
            maxDelayExpression = "${payment.retry.max-interval-ms:10000}"
        )
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TransactionResponse processTransaction(CreateTransactionRequest request) {
        log.info("Processing transaction: idempotencyKey={}, amount={} {}",
                request.getIdempotencyKey(), request.getAmount(), request.getCurrency());

        // --- Validate accounts are different ---
        if (request.getSourceAccountId().equals(request.getDestAccountId())) {
            throw new IllegalArgumentException("Source and destination accounts must be different");
        }

        // --- Acquire locks in consistent UUID order to prevent deadlocks ---
        List<UUID> lockOrder = List.of(request.getSourceAccountId(), request.getDestAccountId())
                .stream()
                .sorted(Comparator.naturalOrder())
                .toList();

        Account first  = lockedAccount(lockOrder.get(0));
        Account second = lockedAccount(lockOrder.get(1));

        // Map back to semantic roles after locking
        Account source = first.getId().equals(request.getSourceAccountId())  ? first  : second;
        Account dest   = first.getId().equals(request.getDestAccountId())    ? first  : second;

        // --- Business rule validation ---
        validateAccountActive(source);
        validateAccountActive(dest);
        validateSufficientFunds(source, request.getAmount());

        // --- Create transaction record (PENDING) ---
        Transaction transaction = Transaction.builder()
                .idempotencyKey(request.getIdempotencyKey())
                .sourceAccount(source)
                .destAccount(dest)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .type(request.getType())
                .status(TransactionStatus.PENDING)
                .reference(request.getReference())
                .build();
        transaction = transactionRepository.save(transaction);

        // --- Debit source, credit dest ---
        BigDecimal newSourceBalance = source.getBalance().subtract(request.getAmount());
        BigDecimal newDestBalance   = dest.getBalance().add(request.getAmount());

        source.setBalance(newSourceBalance);
        dest.setBalance(newDestBalance);

        accountRepository.save(source);
        accountRepository.save(dest);

        // --- Post double-entry ledger entries ---
        ledgerService.postJournalEntry(transaction, source, dest, newSourceBalance, newDestBalance);

        // --- Mark transaction COMPLETED ---
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction = transactionRepository.save(transaction);

        log.info("Transaction {} completed: {} {} from {} to {}",
                transaction.getId(), request.getAmount(), request.getCurrency(),
                source.getAccountNumber(), dest.getAccountNumber());

        return TransactionResponse.from(transaction);
    }

    /** Recovery method — called when all retries are exhausted. */
    @Recover
    public TransactionResponse recoverProcessTransaction(
            TransientDataAccessException ex,
            CreateTransactionRequest request) {
        log.error("Transaction failed after all retries: idempotencyKey={}, error={}",
                request.getIdempotencyKey(), ex.getMessage());
        throw new RuntimeException("Payment processing temporarily unavailable. Please retry later.", ex);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(UUID id) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + id));
        return TransactionResponse.from(tx);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionsForAccount(UUID accountId, Pageable pageable) {
        return transactionRepository.findByAccountId(accountId, pageable)
                .map(TransactionResponse::from);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Account lockedAccount(UUID id) {
        return accountRepository.findByIdWithLock(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    private void validateAccountActive(Account account) {
        if (!account.isActive()) {
            throw new AccountNotActiveException(account.getId(), account.getStatus().name());
        }
    }

    private void validateSufficientFunds(Account account, BigDecimal required) {
        if (account.getBalance().compareTo(required) < 0) {
            throw new InsufficientFundsException(account.getId(), required, account.getBalance());
        }
    }
}
