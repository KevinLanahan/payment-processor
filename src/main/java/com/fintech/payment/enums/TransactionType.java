package com.fintech.payment.enums;

/**
 * Semantic type of a payment event.
 *
 * TRANSFER    – money moves between two real customer accounts
 * DEPOSIT     – money enters the system (e.g. from an external source represented
 *               by a system "cash" account)
 * WITHDRAWAL  – money leaves the system to an external destination
 */
public enum TransactionType {
    TRANSFER,
    DEPOSIT,
    WITHDRAWAL
}
