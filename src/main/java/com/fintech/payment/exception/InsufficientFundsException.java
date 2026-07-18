package com.fintech.payment.exception;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(UUID accountId, BigDecimal required, BigDecimal available) {
        super(String.format(
                "Insufficient funds on account %s: required %s but only %s available",
                accountId, required.toPlainString(), available.toPlainString()));
    }
}
