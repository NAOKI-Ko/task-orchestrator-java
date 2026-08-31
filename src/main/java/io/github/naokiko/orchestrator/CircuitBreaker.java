package io.github.naokiko.orchestrator;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.LongSupplier;

/** Per-job closed/open/half-open circuit breaker. */
public final class CircuitBreaker {
    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final int failureThreshold;
    private final long recoveryNanos;
    private final LongSupplier nanoTime;
    private int failures;
    private long openedAt = Long.MIN_VALUE;

    public CircuitBreaker(int failureThreshold, Duration recoveryTimeout) {
        this(failureThreshold, recoveryTimeout, System::nanoTime);
    }

    CircuitBreaker(int failureThreshold, Duration recoveryTimeout, LongSupplier nanoTime) {
        if (failureThreshold < 1 || recoveryTimeout.isNegative()) {
            throw new IllegalArgumentException("invalid circuit breaker configuration");
        }
        this.failureThreshold = failureThreshold;
        this.recoveryNanos = recoveryTimeout.toNanos();
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    public synchronized State state() {
        if (openedAt == Long.MIN_VALUE) {
            return State.CLOSED;
        }
        return nanoTime.getAsLong() - openedAt >= recoveryNanos ? State.HALF_OPEN : State.OPEN;
    }

    public <T> T execute(Callable<T> operation) throws Exception {
        Objects.requireNonNull(operation, "operation");
        synchronized (this) {
            if (state() == State.OPEN) {
                throw new CircuitOpenException("circuit is open");
            }
        }
        try {
            var result = operation.call();
            synchronized (this) {
                failures = 0;
                openedAt = Long.MIN_VALUE;
            }
            return result;
        } catch (Exception error) {
            synchronized (this) {
                failures++;
                if (failures >= failureThreshold) {
                    openedAt = nanoTime.getAsLong();
                }
            }
            throw error;
        }
    }

    public static final class CircuitOpenException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        CircuitOpenException(String message) {
            super(message);
        }
    }
}

