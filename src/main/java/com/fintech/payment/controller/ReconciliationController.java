package com.fintech.payment.controller;

import com.fintech.payment.dto.ReconciliationReport;
import com.fintech.payment.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    /**
     * POST /api/v1/reconciliation/run
     * Trigger a full reconciliation across all accounts.
     * In production this would be gated behind admin auth.
     */
    @PostMapping("/run")
    public ResponseEntity<ReconciliationReport> runReconciliation() {
        return ResponseEntity.ok(reconciliationService.reconcileAll());
    }

    /**
     * GET /api/v1/reconciliation/accounts/{id}
     * Reconcile a single account on-demand.
     */
    @GetMapping("/accounts/{id}")
    public ResponseEntity<ReconciliationReport> reconcileAccount(@PathVariable UUID id) {
        return ResponseEntity.ok(reconciliationService.reconcileAccount(id));
    }
}
