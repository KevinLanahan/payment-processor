package com.fintech.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.payment.dto.CreateTransactionRequest;
import com.fintech.payment.dto.TransactionResponse;
import com.fintech.payment.model.IdempotencyKey;
import com.fintech.payment.service.IdempotencyService;
import com.fintech.payment.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

/**
 * REST controller for payment transactions.
 *
 * Idempotency flow:
 *   Every POST /transactions request must include an Idempotency-Key header.
 *   1. Check idempotency store — return cached response if key was seen before.
 *   2. Process the transaction.
 *   3. Persist the response in the idempotency store before returning.
 *
 * This guarantees that retried requests (due to network timeouts, client
 * retries, etc.) produce exactly the same observable effect as the first call.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private static final String ENDPOINT = "POST /api/v1/transactions";

    private final TransactionService   transactionService;
    private final IdempotencyService   idempotencyService;
    private final ObjectMapper         objectMapper;

    /**
     * POST /api/v1/transactions
     *
     * Required header: Idempotency-Key: <client-generated UUID>
     *
     * Returns 201 on first successful creation, 200 on idempotent replay.
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateTransactionRequest request) throws Exception {

        // Normalize: override the DTO field with the header value
        // (Belt-and-suspenders: header is authoritative, DTO field kept for docs)
        request.setIdempotencyKey(idempotencyKey);

        String requestBody = objectMapper.writeValueAsString(request);

        // --- Idempotency check ---
        Optional<IdempotencyKey> existing = idempotencyService.findExistingResult(
                idempotencyKey, ENDPOINT, requestBody);

        if (existing.isPresent()) {
            IdempotencyKey cached = existing.get();
            TransactionResponse cachedResponse = objectMapper.readValue(
                    cached.getResponseBody(), TransactionResponse.class);
            // Return 200 (not 201) to signal this is a replay, not a new creation
            return ResponseEntity.status(HttpStatus.OK).body(cachedResponse);
        }

        // --- Process transaction ---
        TransactionResponse response = transactionService.processTransaction(request);

        // --- Persist idempotency result ---
        idempotencyService.saveResult(idempotencyKey, ENDPOINT, requestBody, 201, response);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/v1/transactions/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getTransaction(@PathVariable UUID id) {
        return ResponseEntity.ok(transactionService.getTransaction(id));
    }

    /**
     * GET /api/v1/transactions?accountId={id}&page=0&size=20
     * List transactions for an account, most recent first.
     */
    @GetMapping
    public ResponseEntity<Page<TransactionResponse>> listTransactions(
            @RequestParam UUID accountId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(transactionService.getTransactionsForAccount(accountId, pageable));
    }
}
