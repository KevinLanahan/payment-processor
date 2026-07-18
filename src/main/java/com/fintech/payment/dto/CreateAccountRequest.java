package com.fintech.payment.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateAccountRequest {

    @NotBlank(message = "ownerName is required")
    @Size(max = 255)
    private String ownerName;

    @NotNull(message = "initialBalance is required")
    @DecimalMin(value = "0.00", message = "initialBalance must be non-negative")
    @Digits(integer = 15, fraction = 4)
    private BigDecimal initialBalance;

    @NotBlank(message = "currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be an ISO 4217 code, e.g. USD")
    private String currency;
}
