package io.github.naokiko.orchestrator;

import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Cooperative cancellation view passed to jobs.
 *
 * @param jobId identity of the executing job
 * @param attempt one-based attempt number
 * @param cancellationRequested live workflow-cancellation probe
 */
public record JobContext(JobId jobId, int attempt, BooleanSupplier cancellationRequested) {
    /** Validates that the identity, attempt, and cancellation probe form a usable context. */
    public JobContext {
        if (jobId == null || attempt < 1 || cancellationRequested == null) {
            throw new IllegalArgumentException("invalid job context");
        }
    }

    /**
     * Fails the current attempt when workflow cancellation has been requested.
     *
     * @throws CancellationException when cancellation is currently requested
     */
    public void throwIfCancelled() {
        if (cancellationRequested.getAsBoolean()) {
            throw new CancellationException("workflow cancellation requested");
        }
    }
}
