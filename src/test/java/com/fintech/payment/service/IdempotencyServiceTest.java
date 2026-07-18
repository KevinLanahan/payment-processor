package com.fintech.payment.service;

import com.fintech.payment.exception.IdempotencyConflictException;
import com.fintech.payment.model.IdempotencyKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IdempotencyServiceTest {

    @Autowired private IdempotencyService idempotencyService;

    private static final String ENDPOINT = "POST /api/v1/transactions";

    @Test
    @DisplayName("First request: no existing result found")
    void firstRequest_returnsEmpty() {
        Optional<IdempotencyKey> result = idempotencyService.findExistingResult(
                UUID.randomUUID().toString(), ENDPOINT, "{\"amount\":100}");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Retry with same key and body: returns cached result")
    void retry_samekeyAndBody_returnsCachedResult() {
        String key  = UUID.randomUUID().toString();
        String body = "{\"amount\":100}";

        idempotencyService.saveResult(key, ENDPOINT, body, 201, "response-payload");

        Optional<IdempotencyKey> result = idempotencyService.findExistingResult(key, ENDPOINT, body);

        assertThat(result).isPresent();
        assertThat(result.get().getResponseStatus()).isEqualTo(201);
        assertThat(result.get().getResponseBody()).isEqualTo("\"response-payload\"");
    }

    @Test
    @DisplayName("Same key, different body: throws IdempotencyConflictException")
    void retry_sameKeyDifferentBody_throwsConflict() {
        String key        = UUID.randomUUID().toString();
        String origBody   = "{\"amount\":100}";
        String retryBody  = "{\"amount\":200}"; // different!

        idempotencyService.saveResult(key, ENDPOINT, origBody, 201, "response");

        assertThatThrownBy(() ->
                idempotencyService.findExistingResult(key, ENDPOINT, retryBody))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining(key);
    }

    @Test
    @DisplayName("Same key, different endpoint: treated as independent requests")
    void retry_sameKeyDifferentEndpoint_independent() {
        String key  = UUID.randomUUID().toString();
        String body = "{\"amount\":100}";

        idempotencyService.saveResult(key, "POST /api/v1/other", body, 200, "other-response");

        // Should NOT find the result because endpoint differs
        Optional<IdempotencyKey> result = idempotencyService.findExistingResult(
                key, ENDPOINT, body);

        assertThat(result).isEmpty();
    }
}
