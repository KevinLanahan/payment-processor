package com.fintech.payment.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Output of a reconciliation run.
 *
 * For each account, the service computes:
 *   ledgerBalance = SUM(CREDIT amounts) - SUM(DEBIT amounts)  from ledger_entries
 *
 * If ledgerBalance != account.balance, a discrepancy is recorded.
 * A healthy system should always produce zero discrepancies.
 */
@Data
@Builder
public class ReconciliationReport {

    private OffsetDateTime runAt;
    private int totalAccountsChecked;
    private int discrepanciesFound;
    private List<Discrepancy> discrepancies;

    @Data
    @Builder
    public static class Discrepancy {
        private UUID accountId;
        private String accountNumber;
        /** Balance stored in the accounts table. */
        private BigDecimal storedBalance;
        /** Balance recomputed from the ledger entries. */
        private BigDecimal ledgerBalance;
        /** Difference: storedBalance - ledgerBalance */
        private BigDecimal delta;
    }
}
