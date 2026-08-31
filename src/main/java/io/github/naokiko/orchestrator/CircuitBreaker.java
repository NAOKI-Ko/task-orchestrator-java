package io.github.naokiko.orchestrator;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.LongSupplier;

/** Per-job closed/open/half-open circuit breaker. */
public final class CircuitBreaker {
    /** Circuit availability derived from failure history and elapsed recovery time. */
    public enum State {
        /** Calls are admitted normally. */
        CLOSED,
        /** Calls are rejected until the recovery timeout elapses. */
        OPEN,
        /** The recovery timeout elapsed and a trial call may proceed. */
        HALF_OPEN
    }

    private final int failureThreshold;
    private final long recoveryNanos;
    private final LongSupplier nanoTime;
    private int failures;
    private long openedAt = Long.MIN_VALUE;

    /**
     * Creates a circuit breaker using {@link System#nanoTime()} as its monotonic clock.
     *
     * @param failureThreshold consecutive failures required to open the circuit
     * @param recoveryTimeout duration before an open circuit becomes half-open
     */
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

    /**
     * Returns the availability state at the current monotonic time.
     *
     * @return closed, open, or half-open state
     */
    public synchronized State state() {
        if (openedAt == Long.MIN_VALUE) {
            return State.CLOSED;
        }
        return nanoTime.getAsLong() - openedAt >= recoveryNanos ? State.HALF_OPEN : State.OPEN;
    }

    /**
     * Executes an operation when the circuit admits calls and updates failure history.
     *
     * @param operation operation guarded by the circuit
     * @param <T> operation result type
     * @return operation result
     * @throws CircuitOpenException when the circuit is open
     * @throws Exception when the operation fails
     */
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

    /** Indicates that a guarded operation was rejected because the circuit is open. */
    public static final class CircuitOpenException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        CircuitOpenException(String message) {
            super(message);
        }
    }
}
