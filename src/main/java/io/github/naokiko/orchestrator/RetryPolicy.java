package io.github.naokiko.orchestrator;

import java.time.Duration;
import java.util.Objects;

/** Immutable bounded exponential backoff policy. */
public record RetryPolicy(int maxAttempts, Duration initialDelay, Duration maxDelay, double multiplier, double jitter) {
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

    public static RetryPolicy none() {
        return new RetryPolicy(1, Duration.ZERO, Duration.ZERO, 1, 0);
    }

    public Duration delayFor(int attempt, double randomValue) {
        var exponential = initialDelay.toNanos() * Math.pow(multiplier, Math.max(0, attempt - 1));
        var bounded = Math.min(maxDelay.toNanos(), exponential);
        var factor = 1 + jitter * (2 * randomValue - 1);
        var nanos = Math.max(0, Math.min(maxDelay.toNanos(), Math.round(bounded * factor)));
        return Duration.ofNanos(nanos);
    }
}

