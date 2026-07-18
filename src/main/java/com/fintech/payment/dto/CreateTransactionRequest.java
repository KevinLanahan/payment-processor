package com.fintech.payment.dto;

import com.fintech.payment.enums.TransactionType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateTransactionRequest {

    /**
     * Set by the controller from the Idempotency-Key request header.
     * Not validated here — the controller enforces presence via @RequestHeader.
     */
    private String idempotencyKey;

    @NotNull(message = "sourceAccountId is required")
    private UUID sourceAccountId;

    @NotNull(message = "destAccountId is required")
    private UUID destAccountId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    @Digits(integer = 15, fraction = 4)
    private BigDecimal amount;

    @NotBlank(message = "currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be an ISO 4217 code, e.g. USD")
    private String currency;

    @NotNull(message = "type is required")
    private TransactionType type;

    @Size(max = 255)
    private String reference;
}
