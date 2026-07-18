package com.fintech.payment.enums;

/**
 * Lifecycle of a payment transaction.
 *
 * State machine:
 *   PENDING → COMPLETED  (happy path)
 *   PENDING → FAILED     (business rule violation, e.g. insufficient funds)
 *   COMPLETED → REVERSED (manual or automated reversal)
 */
public enum TransactionStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REVERSED
}
