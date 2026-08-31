package io.github.naokiko.orchestrator;

import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Cooperative cancellation view passed to jobs. */
public record JobContext(JobId jobId, int attempt, BooleanSupplier cancellationRequested) {
    public JobContext {
        if (jobId == null || attempt < 1 || cancellationRequested == null) {
            throw new IllegalArgumentException("invalid job context");
        }
    }

    public void throwIfCancelled() {
        if (cancellationRequested.getAsBoolean()) {
            throw new CancellationException("workflow cancellation requested");
        }
    }
}

