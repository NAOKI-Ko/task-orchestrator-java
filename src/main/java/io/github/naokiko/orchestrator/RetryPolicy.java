package io.github.naokiko.orchestrator;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable bounded exponential backoff policy.
 *
 * @param maxAttempts maximum number of attempts including the initial execution
 * @param initialDelay delay before the first retry
 * @param maxDelay upper bound applied to every retry delay
 * @param multiplier exponential growth factor, at least {@code 1}
 * @param jitter symmetric jitter ratio in the inclusive range {@code [0, 1]}
 */
public record RetryPolicy(int maxAttempts, Duration initialDelay, Duration maxDelay, double multiplier, double jitter) {
    /** Validates retry counts, ordered delays, multiplier, and jitter bounds. */
    public RetryPolicy {
        Objects.requireNonNull(initialDelay, "initialDelay");
        Objects.requireNonNull(maxDelay, "maxDelay");
        if (maxAttempts < 1 || initialDelay.isNegative() || maxDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("attempts and delays must be positive and ordered");
        }
        if (multiplier < 1 || jitter < 0 || jitter > 1) {
            throw new IllegalArgumentException("multiplier or jitter is outside its valid range");
        }
    }

    /**
     * Returns a policy that performs exactly one attempt without backoff.
     *
     * @return no-retry policy
     */
    public static RetryPolicy none() {
        return new RetryPolicy(1, Duration.ZERO, Duration.ZERO, 1, 0);
    }

    /**
     * Calculates a bounded, jittered delay for a failed attempt.
     *
     * @param attempt one-based failed attempt number
     * @param randomValue random sample normally in the inclusive range {@code [0, 1]}
     * @return non-negative delay no greater than {@link #maxDelay()}
     */
    public Duration delayFor(int attempt, double randomValue) {
        var exponential = initialDelay.toNanos() * Math.pow(multiplier, Math.max(0, attempt - 1));
        var bounded = Math.min(maxDelay.toNanos(), exponential);
        var factor = 1 + jitter * (2 * randomValue - 1);
        var nanos = Math.max(0, Math.min(maxDelay.toNanos(), Math.round(bounded * factor)));
        return Duration.ofNanos(nanos);
    }
}
