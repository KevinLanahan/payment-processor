package com.fintech.payment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.Map;

/**
 * Configures Spring Retry for downstream failures.
 *
 * Strategy: exponential backoff with jitter to avoid thundering-herd.
 * Only transient exceptions (network errors, lock timeouts) should be retried.
 * Business exceptions (insufficient funds, not found) must NOT be retried.
 *
 * The @Retryable annotation on service methods references these settings.
 */
@Configuration
public class RetryConfig {

    @Value("${payment.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${payment.retry.initial-interval-ms:500}")
    private long initialIntervalMs;

    @Value("${payment.retry.multiplier:2.0}")
    private double multiplier;

    @Value("${payment.retry.max-interval-ms:10000}")
    private long maxIntervalMs;

    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate template = new RetryTemplate();

        // Backoff: 500ms → 1000ms → 2000ms (capped at 10s)
        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(initialIntervalMs);
        backOff.setMultiplier(multiplier);
        backOff.setMaxInterval(maxIntervalMs);
        template.setBackOffPolicy(backOff);

        // Only retry on transient exceptions
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(maxAttempts, Map.of(
                org.springframework.dao.TransientDataAccessException.class, true,
                org.springframework.dao.QueryTimeoutException.class, true,
                jakarta.persistence.PessimisticLockException.class, true,
                java.net.ConnectException.class, true
        ), true);
        template.setRetryPolicy(retryPolicy);

        return template;
    }
}
