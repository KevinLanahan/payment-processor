package com.fintech.payment.controller;

import com.fintech.payment.dto.AccountResponse;
import com.fintech.payment.dto.CreateAccountRequest;
import com.fintech.payment.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /**
     * POST /api/v1/accounts
     * Create a new account with an initial balance.
     */
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        AccountResponse response = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/v1/accounts/{id}
     * Retrieve account details including current balance.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.getAccount(id));
    }

    /**
     * POST /api/v1/accounts/{id}/suspend
     * Suspend an account to block future transactions.
     */
    @PostMapping("/{id}/suspend")
    public ResponseEntity<AccountResponse> suspendAccount(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.suspendAccount(id));
    }

    /**
     * POST /api/v1/accounts/{id}/reactivate
     * Reactivate a suspended account.
     */
    @PostMapping("/{id}/reactivate")
    public ResponseEntity<AccountResponse> reactivateAccount(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.reactivateAccount(id));
    }
}
