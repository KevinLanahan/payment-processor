package com.fintech.payment.service;

import com.fintech.payment.dto.AccountResponse;
import com.fintech.payment.dto.CreateAccountRequest;
import com.fintech.payment.enums.AccountStatus;
import com.fintech.payment.enums.TransactionStatus;
import com.fintech.payment.enums.TransactionType;
import com.fintech.payment.exception.AccountNotFoundException;
import com.fintech.payment.model.Account;
import com.fintech.payment.model.Transaction;
import com.fintech.payment.repository.AccountRepository;
import com.fintech.payment.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    /**
     * Reserved UUID for the bank's capital account.
     * Inserted via V5 migration. Acts as the double-entry counterpart
     * for all opening-balance deposits so every dollar in the system
     * has a matching ledger entry from day one.
     */
    static final UUID SYSTEM_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final AccountRepository     accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerService         ledgerService;

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        String accountNumber = generateAccountNumber();

        // New account starts at 0 — the initial deposit is posted as a proper
        // DEPOSIT transaction so ledger entries exist from the very first dollar.
        Account account = Account.builder()
                .accountNumber(accountNumber)
                .ownerName(request.getOwnerName())
                .balance(BigDecimal.ZERO)
                .currency(request.getCurrency())
                .status(AccountStatus.ACTIVE)
                .version(0L)
                .build();

        account = accountRepository.save(account);

        if (request.getInitialBalance().compareTo(BigDecimal.ZERO) > 0) {
            postOpeningDeposit(account, request.getInitialBalance());
        }

        log.info("Created account {} for owner '{}'", account.getAccountNumber(), account.getOwnerName());
        return AccountResponse.from(accountRepository.findById(account.getId()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse suspendAccount(UUID id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
        account.setStatus(AccountStatus.SUSPENDED);
        log.info("Suspended account {}", account.getAccountNumber());
        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional
    public AccountResponse reactivateAccount(UUID id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
        account.setStatus(AccountStatus.ACTIVE);
        log.info("Reactivated account {}", account.getAccountNumber());
        return AccountResponse.from(accountRepository.save(account));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Posts an opening-balance DEPOSIT from the bank capital account to the
     * newly created account, producing proper double-entry ledger entries.
     *
     * This means reconciliation will always pass for new accounts — the ledger
     * balance exactly matches the stored balance from the moment of creation.
     */
    private void postOpeningDeposit(Account dest, BigDecimal amount) {
        Account systemAccount = accountRepository.findByIdWithLock(SYSTEM_ACCOUNT_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "System bank capital account not found — ensure V5 migration has run"));

        Transaction depositTx = Transaction.builder()
                .idempotencyKey("OPENING_DEPOSIT_" + dest.getId())
                .sourceAccount(systemAccount)
                .destAccount(dest)
                .amount(amount)
                .currency(dest.getCurrency())
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.COMPLETED)
                .reference("Opening balance deposit")
                .build();
        depositTx = transactionRepository.save(depositTx);

        // Debit system account, credit new account
        BigDecimal newSystemBalance = systemAccount.getBalance().subtract(amount);
        BigDecimal newDestBalance   = amount; // account started at 0

        systemAccount.setBalance(newSystemBalance);
        dest.setBalance(newDestBalance);
        accountRepository.save(systemAccount);
        accountRepository.save(dest);

        ledgerService.postJournalEntry(depositTx, systemAccount, dest, newSystemBalance, newDestBalance);

        log.info("Posted opening deposit of {} {} to account {}", amount, dest.getCurrency(), dest.getAccountNumber());
    }

    private String generateAccountNumber() {
        String candidate;
        do {
            candidate = "ACC" + String.format("%012d", (long) (Math.random() * 1_000_000_000_000L));
        } while (accountRepository.existsByAccountNumber(candidate));
        return candidate;
    }
}
