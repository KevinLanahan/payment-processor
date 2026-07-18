package com.fintech.payment.exception;

import java.util.UUID;

public class AccountNotActiveException extends RuntimeException {

    public AccountNotActiveException(UUID accountId, String status) {
        super(String.format("Account %s is not eligible for transactions — current status: %s", accountId, status));
    }
}
