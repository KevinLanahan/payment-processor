package com.fintech.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.payment.exception.IdempotencyConflictException;
import com.fintech.payment.model.IdempotencyKey;
import com.fintech.payment.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Manages idempotency for write endpoints.
 *
 * Flow:
 *   1. Client sends request with Idempotency-Key header.
 *   2. Service calls {@link #findExistingResult}: if present and non-expired,
 *      return the cached response immediately (no business logic executed).
 *   3. If absent, execute business logic, then call {@link #saveResult} to
 *      persist the response for future retries.
 *
 * Conflict detection:
 *   If the same key is seen again with a DIFFERENT request body (detected via
 *   SHA-256 hash comparison), we return 409 — the client made a mistake.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    @Value("${payment.idempotency.ttl-hours:24}")
    private int ttlHours;

    /**
     * Looks up a previous result for this key + endpoint pair.
     *
     * @return the stored {@link IdempotencyKey} if valid (non-expired), or empty.
     * @throws IdempotencyConflictException if the key exists but with a different request body.
     */
    @Transactional(readOnly = true)
    public Optional<IdempotencyKey> findExistingResult(
            String idempotencyKey,
            String endpoint,
            String requestBody) {

        return idempotencyKeyRepository
                .findByIdempotencyKeyAndEndpoint(idempotencyKey, endpoint)
                .filter(stored -> {
                    if (stored.isExpired()) {
                        return false; // treat as not found; new request can proceed
                    }
                    String incomingHash = sha256(requestBody);
                    if (!incomingHash.equals(stored.getRequestHash())) {
                        throw new IdempotencyConflictException(idempotencyKey);
                    }
                    log.info("Idempotent replay for key={} endpoint={}", idempotencyKey, endpoint);
                    return true;
                });
    }

    /** Persists the response so future retries can be served from cache. */
    @Transactional
    public void saveResult(
            String idempotencyKey,
            String endpoint,
            String requestBody,
            int responseStatus,
            Object responseBody) {

        String responseJson;
        try {
            responseJson = objectMapper.writeValueAsString(responseBody);
        } catch (Exception e) {
            log.error("Failed to serialize idempotency response body", e);
            return; // non-fatal: idempotency store failure shouldn't abort the request
        }

        IdempotencyKey record = IdempotencyKey.builder()
                .idempotencyKey(idempotencyKey)
                .endpoint(endpoint)
                .requestHash(sha256(requestBody))
                .responseStatus(responseStatus)
                .responseBody(responseJson)
                .expiresAt(OffsetDateTime.now().plusHours(ttlHours))
                .build();

        idempotencyKeyRepository.save(record);
        log.debug("Saved idempotency record for key={}", idempotencyKey);
    }

    /** Scheduled cleanup: purge expired idempotency records once per hour. */
    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void purgeExpiredKeys() {
        int deleted = idempotencyKeyRepository.deleteExpiredBefore(OffsetDateTime.now());
        if (deleted > 0) {
            log.info("Purged {} expired idempotency key records", deleted);
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
