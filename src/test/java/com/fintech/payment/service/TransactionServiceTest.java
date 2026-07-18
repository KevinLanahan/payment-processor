package com.fintech.payment.service;

import com.fintech.payment.dto.CreateTransactionRequest;
import com.fintech.payment.dto.TransactionResponse;
import com.fintech.payment.enums.AccountStatus;
import com.fintech.payment.enums.TransactionStatus;
import com.fintech.payment.enums.TransactionType;
import com.fintech.payment.exception.AccountNotActiveException;
import com.fintech.payment.exception.InsufficientFundsException;
import com.fintech.payment.model.Account;
import com.fintech.payment.model.LedgerEntry;
import com.fintech.payment.repository.AccountRepository;
import com.fintech.payment.repository.LedgerEntryRepository;
import com.fintech.payment.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for TransactionService.
 *
 * These tests run against H2 in PostgreSQL-compatibility mode with the actual
 * Spring context wired up. They verify the banking invariants that matter most:
 *
 *   1. Double-entry: every transaction produces exactly 2 ledger entries
 *   2. Balance accuracy: account balances updated correctly after transfer
 *   3. Double-entry amounts: DEBIT.amount == CREDIT.amount
 *   4. Net balance conservation: total money in the system is unchanged by a transfer
 *   5. Insufficient funds: rejected with correct exception
 *   6. Suspended account: rejected with correct exception
 *   7. Same account transfer: rejected
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TransactionServiceTest {

    @Autowired private TransactionService    transactionService;
    @Autowired private AccountRepository     accountRepository;
    @Autowired private LedgerEntryRepository ledgerEntryRepository;
    @Autowired private TransactionRepository transactionRepository;

    private Account alice;
    private Account bob;

    @BeforeEach
    void setUp() {
        alice = accountRepository.save(Account.builder()
                .accountNumber("ACC000000000001")
                .ownerName("Alice")
                .balance(new BigDecimal("1000.00"))
                .currency("USD")
                .status(AccountStatus.ACTIVE)
                .version(0L)
                .build());

        bob = accountRepository.save(Account.builder()
                .accountNumber("ACC000000000002")
                .ownerName("Bob")
                .balance(new BigDecimal("500.00"))
                .currency("USD")
                .status(AccountStatus.ACTIVE)
                .version(0L)
                .build());
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Successful transfer: balances updated correctly")
    void transfer_updatesBalancesCorrectly() {
        TransactionResponse response = processTransfer(alice, bob, "250.00");

        assertThat(response.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(response.getAmount()).isEqualByComparingTo("250.00");

        Account updatedAlice = accountRepository.findById(alice.getId()).orElseThrow();
        Account updatedBob   = accountRepository.findById(bob.getId()).orElseThrow();

        assertThat(updatedAlice.getBalance()).isEqualByComparingTo("750.00");
        assertThat(updatedBob.getBalance()).isEqualByComparingTo("750.00");
    }

    @Test
    @DisplayName("Double-entry: exactly two ledger entries created per transaction")
    void transfer_createsExactlyTwoLedgerEntries() {
        TransactionResponse response = processTransfer(alice, bob, "100.00");

        List<LedgerEntry> entries = ledgerEntryRepository
                .findByTransactionId(response.getId());

        assertThat(entries).hasSize(2);
    }

    @Test
    @DisplayName("Double-entry: DEBIT and CREDIT amounts are equal")
    void transfer_debitAndCreditAmountsAreEqual() {
        TransactionResponse response = processTransfer(alice, bob, "300.00");

        List<LedgerEntry> entries = ledgerEntryRepository
                .findByTransactionId(response.getId());

        BigDecimal debitTotal  = entries.stream()
                .filter(e -> e.getEntryType().name().equals("DEBIT"))
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal creditTotal = entries.stream()
                .filter(e -> e.getEntryType().name().equals("CREDIT"))
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(debitTotal).isEqualByComparingTo(creditTotal);
        assertThat(debitTotal).isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("Money conservation: total system balance unchanged after transfer")
    void transfer_conservesTotalSystemBalance() {
        BigDecimal totalBefore = alice.getBalance().add(bob.getBalance());

        processTransfer(alice, bob, "400.00");

        Account updatedAlice = accountRepository.findById(alice.getId()).orElseThrow();
        Account updatedBob   = accountRepository.findById(bob.getId()).orElseThrow();
        BigDecimal totalAfter = updatedAlice.getBalance().add(updatedBob.getBalance());

        assertThat(totalAfter).isEqualByComparingTo(totalBefore);
    }

    @Test
    @DisplayName("Running balance: ledger entry captures correct post-entry balance")
    void transfer_runningBalanceIsCorrectlyRecorded() {
        TransactionResponse response = processTransfer(alice, bob, "200.00");

        List<LedgerEntry> entries = ledgerEntryRepository
                .findByTransactionId(response.getId());

        LedgerEntry debitEntry = entries.stream()
                .filter(e -> e.getEntryType().name().equals("DEBIT"))
                .findFirst().orElseThrow();

        LedgerEntry creditEntry = entries.stream()
                .filter(e -> e.getEntryType().name().equals("CREDIT"))
                .findFirst().orElseThrow();

        // Alice started at 1000, transferred 200 → should show 800
        assertThat(debitEntry.getRunningBalance()).isEqualByComparingTo("800.00");
        // Bob started at 500, received 200 → should show 700
        assertThat(creditEntry.getRunningBalance()).isEqualByComparingTo("700.00");
    }

    // -------------------------------------------------------------------------
    // Business rule violations
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Insufficient funds: throws InsufficientFundsException")
    void transfer_throwsWhenInsufficientFunds() {
        assertThatThrownBy(() -> processTransfer(alice, bob, "9999.00"))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Insufficient funds");
    }

    @Test
    @DisplayName("Insufficient funds: no ledger entries created on failure")
    void transfer_noLedgerEntriesOnFailure() {
        long entriesBefore = ledgerEntryRepository.count();

        assertThatThrownBy(() -> processTransfer(alice, bob, "9999.00"))
                .isInstanceOf(InsufficientFundsException.class);

        long entriesAfter = ledgerEntryRepository.count();
        assertThat(entriesAfter).isEqualTo(entriesBefore); // rollback happened
    }

    @Test
    @DisplayName("Suspended source account: throws AccountNotActiveException")
    void transfer_throwsWhenSourceAccountSuspended() {
        alice.setStatus(AccountStatus.SUSPENDED);
        accountRepository.save(alice);

        assertThatThrownBy(() -> processTransfer(alice, bob, "100.00"))
                .isInstanceOf(AccountNotActiveException.class);
    }

    @Test
    @DisplayName("Suspended destination account: throws AccountNotActiveException")
    void transfer_throwsWhenDestAccountSuspended() {
        bob.setStatus(AccountStatus.SUSPENDED);
        accountRepository.save(bob);

        assertThatThrownBy(() -> processTransfer(alice, bob, "100.00"))
                .isInstanceOf(AccountNotActiveException.class);
    }

    @Test
    @DisplayName("Same account transfer: throws IllegalArgumentException")
    void transfer_throwsWhenSameAccount() {
        assertThatThrownBy(() -> processTransfer(alice, alice, "100.00"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different");
    }

    @Test
    @DisplayName("Exact balance transfer: succeeds and leaves source at zero")
    void transfer_exactBalanceLeaveSourceAtZero() {
        TransactionResponse response = processTransfer(alice, bob, "1000.00");

        assertThat(response.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        Account updatedAlice = accountRepository.findById(alice.getId()).orElseThrow();
        assertThat(updatedAlice.getBalance()).isEqualByComparingTo("0.00");
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private TransactionResponse processTransfer(Account source, Account dest, String amount) {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setIdempotencyKey(UUID.randomUUID().toString());
        request.setSourceAccountId(source.getId());
        request.setDestAccountId(dest.getId());
        request.setAmount(new BigDecimal(amount));
        request.setCurrency("USD");
        request.setType(TransactionType.TRANSFER);
        return transactionService.processTransaction(request);
    }
}
