package com.fintech.payment.service;

import com.fintech.payment.enums.EntryType;
import com.fintech.payment.model.Account;
import com.fintech.payment.model.LedgerEntry;
import com.fintech.payment.model.Transaction;
import com.fintech.payment.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Manages the immutable double-entry ledger.
 *
 * Every call to {@link #postJournalEntry} creates exactly two rows atomically:
 * a DEBIT on the source account and a CREDIT on the destination account.
 * Both rows are created in the same database transaction, so they either both
 * succeed or both fail — preserving the accounting equation at all times.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    /**
     * Posts a balanced journal entry for a completed transaction.
     *
     * @param transaction  the completed payment
     * @param source       source account (already debited — new balance passed in)
     * @param dest         destination account (already credited — new balance passed in)
     * @param sourceNewBal balance of source account after the debit
     * @param destNewBal   balance of dest account after the credit
     */
    @Transactional
    public void postJournalEntry(
            Transaction transaction,
            Account source,
            Account dest,
            BigDecimal sourceNewBal,
            BigDecimal destNewBal) {

        BigDecimal amount = transaction.getAmount();
        String currency  = transaction.getCurrency();

        // Debit: money leaves the source account
        LedgerEntry debit = LedgerEntry.builder()
                .transaction(transaction)
                .account(source)
                .entryType(EntryType.DEBIT)
                .amount(amount)
                .currency(currency)
                .runningBalance(sourceNewBal)
                .build();

        // Credit: money enters the destination account
        LedgerEntry credit = LedgerEntry.builder()
                .transaction(transaction)
                .account(dest)
                .entryType(EntryType.CREDIT)
                .amount(amount)
                .currency(currency)
                .runningBalance(destNewBal)
                .build();

        ledgerEntryRepository.save(debit);
        ledgerEntryRepository.save(credit);

        log.info("Posted journal entry for tx={}: DEBIT {} on account={}, CREDIT {} on account={}",
                transaction.getId(), amount, source.getId(), amount, dest.getId());

        // Verify the double-entry invariant was just satisfied
        validateJournalEntry(debit, credit);
    }

    /** Returns the account's balance as computed from all its ledger entries. */
    @Transactional(readOnly = true)
    public BigDecimal computeLedgerBalance(UUID accountId) {
        return ledgerEntryRepository.computeLedgerBalanceForAccount(accountId);
    }

    @Transactional(readOnly = true)
    public Page<LedgerEntry> getLedgerHistory(UUID accountId, Pageable pageable) {
        return ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);
    }

    /**
     * Asserts the double-entry invariant: both sides of the journal entry must
     * have equal amounts. This is a defensive check — the data model already
     * enforces this structurally, but explicit validation provides a clear
     * error message if something goes wrong in the business logic layer.
     */
    private void validateJournalEntry(LedgerEntry debit, LedgerEntry credit) {
        if (debit.getAmount().compareTo(credit.getAmount()) != 0) {
            throw new IllegalStateException(String.format(
                    "Double-entry invariant violated! DEBIT amount %s ≠ CREDIT amount %s for transaction %s",
                    debit.getAmount(), credit.getAmount(), debit.getTransaction().getId()));
        }
        if (debit.getEntryType() != EntryType.DEBIT) {
            throw new IllegalStateException("First entry must be DEBIT");
        }
        if (credit.getEntryType() != EntryType.CREDIT) {
            throw new IllegalStateException("Second entry must be CREDIT");
        }
    }
}
