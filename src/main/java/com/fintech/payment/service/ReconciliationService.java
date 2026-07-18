package com.fintech.payment.service;

import com.fintech.payment.dto.ReconciliationReport;
import com.fintech.payment.model.Account;
import com.fintech.payment.repository.AccountRepository;
import com.fintech.payment.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Reconciliation service — validates the double-entry invariant across all accounts.
 *
 * For each account, we:
 *   1. Read account.balance (the denormalized snapshot)
 *   2. Recompute the "true" balance by summing all ledger entries:
 *      ledgerBalance = SUM(CREDIT amounts) - SUM(DEBIT amounts)
 *   3. If they differ, flag a discrepancy
 *
 * A healthy system produces zero discrepancies. Discrepancies indicate:
 *   - A bug in TransactionService (updated account.balance without posting ledger entries)
 *   - Direct DB manipulation bypassing the application layer
 *   - A crash mid-transaction that left partial state (should be impossible with
 *     @Transactional, but worth detecting)
 *
 * Run this on a schedule (e.g. nightly) or on-demand via the REST API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final AccountRepository    accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    /**
     * REPEATABLE_READ isolation ensures we see a consistent snapshot of both
     * account balances and ledger entries throughout the reconciliation run.
     * Without this, a concurrent transaction completing mid-run could cause us
     * to see the updated account.balance but not yet the new ledger entries
     * (or vice versa), producing false positives.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
    public ReconciliationReport reconcileAll() {
        log.info("Starting reconciliation run...");

        List<Account> accounts = accountRepository.findAll();
        List<ReconciliationReport.Discrepancy> discrepancies = new ArrayList<>();

        for (Account account : accounts) {
            // Skip the system bank capital account — its seed balance is set via
            // SQL migration (not a transaction), so it will always show a discrepancy.
            if (account.getId().equals(com.fintech.payment.service.AccountService.SYSTEM_ACCOUNT_ID)) {
                continue;
            }
            BigDecimal storedBalance = account.getBalance();
            BigDecimal ledgerBalance = ledgerEntryRepository
                    .computeLedgerBalanceForAccount(account.getId());

            // Use compareTo, not equals — BigDecimal(1.0) != BigDecimal(1.00) via equals
            if (storedBalance.compareTo(ledgerBalance) != 0) {
                BigDecimal delta = storedBalance.subtract(ledgerBalance);
                log.warn("RECONCILIATION DISCREPANCY: account={} storedBalance={} ledgerBalance={} delta={}",
                        account.getAccountNumber(), storedBalance, ledgerBalance, delta);

                discrepancies.add(ReconciliationReport.Discrepancy.builder()
                        .accountId(account.getId())
                        .accountNumber(account.getAccountNumber())
                        .storedBalance(storedBalance)
                        .ledgerBalance(ledgerBalance)
                        .delta(delta)
                        .build());
            }
        }

        ReconciliationReport report = ReconciliationReport.builder()
                .runAt(OffsetDateTime.now())
                .totalAccountsChecked(accounts.size())
                .discrepanciesFound(discrepancies.size())
                .discrepancies(discrepancies)
                .build();

        if (discrepancies.isEmpty()) {
            log.info("Reconciliation complete: {} accounts checked, no discrepancies.", accounts.size());
        } else {
            log.error("Reconciliation complete: {} discrepancies found across {} accounts — INVESTIGATE IMMEDIATELY.",
                    discrepancies.size(), accounts.size());
        }

        return report;
    }

    /**
     * Reconcile a single account. Useful for targeted investigation after
     * a discrepancy is found, or as a lightweight check during transaction audits.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
    public ReconciliationReport reconcileAccount(java.util.UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new com.fintech.payment.exception.AccountNotFoundException(accountId));

        BigDecimal storedBalance = account.getBalance();
        BigDecimal ledgerBalance = ledgerEntryRepository.computeLedgerBalanceForAccount(accountId);
        List<ReconciliationReport.Discrepancy> discrepancies = new ArrayList<>();

        if (storedBalance.compareTo(ledgerBalance) != 0) {
            discrepancies.add(ReconciliationReport.Discrepancy.builder()
                    .accountId(accountId)
                    .accountNumber(account.getAccountNumber())
                    .storedBalance(storedBalance)
                    .ledgerBalance(ledgerBalance)
                    .delta(storedBalance.subtract(ledgerBalance))
                    .build());
        }

        return ReconciliationReport.builder()
                .runAt(OffsetDateTime.now())
                .totalAccountsChecked(1)
                .discrepanciesFound(discrepancies.size())
                .discrepancies(discrepancies)
                .build();
    }
}
