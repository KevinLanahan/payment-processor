package com.fintech.payment.enums;

/**
 * Double-entry bookkeeping entry type.
 *
 * For an asset account (like a checking account):
 *   DEBIT  → balance decreases (money going out)
 *   CREDIT → balance increases (money coming in)
 *
 * Every transaction must produce exactly one DEBIT and one CREDIT entry,
 * and both amounts must be equal — preserving the accounting equation.
 */
public enum EntryType {
    DEBIT,
    CREDIT
}
