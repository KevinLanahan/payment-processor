package com.fintech.payment.exception;

/**
 * Thrown when a client reuses an idempotency key with a different request body.
 * This is a client error (409 Conflict) — the key must uniquely identify
 * one logical operation; sending a different payload with the same key is a bug.
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String key) {
        super("Idempotency key '" + key + "' was already used with a different request payload. " +
              "Each unique operation must use a unique key.");
    }
}
