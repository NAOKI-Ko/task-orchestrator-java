package io.github.naokiko.orchestrator;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable aggregate counts derived from a {@link WorkflowResult}.
 *
 * <p>Dependency failures are a subset of {@link #failed()}; cancellations are counted separately.
 *
 * @param total total job results
 * @param succeeded successful jobs
 * @param failed failed jobs, including dependency-caused failures
 * @param cancelled cancelled jobs
 * @param dependencyFailures failures caused by an unsuccessful dependency
 * @param duration total workflow duration
 */
public record ExecutionSummary(
        int total,
        int succeeded,
        int failed,
        int cancelled,
        int dependencyFailures,
        Duration duration) {
    /** Validates count consistency and duration. */
    public ExecutionSummary {
        Objects.requireNonNull(duration, "duration");
        if (total < 0 || succeeded < 0 || failed < 0 || cancelled < 0 || dependencyFailures < 0) {
            throw new IllegalArgumentException("summary counts must be non-negative");
        }
        if (succeeded + failed + cancelled != total || dependencyFailures > failed) {
            throw new IllegalArgumentException("summary counts are inconsistent");
        }
        if (duration.isNegative()) {
            throw new IllegalArgumentException("summary duration must be non-negative");
        }
    }

    /**
     * Derives counts from the exhaustive job-result variants.
     *
     * @param result workflow result to summarize
     * @return immutable execution summary
     */
    public static ExecutionSummary from(WorkflowResult result) {
        Objects.requireNonNull(result, "result");
        var succeeded = 0;
        var failed = 0;
        var cancelled = 0;
        var dependencyFailures = 0;
        for (var job : result.jobs().values()) {
            switch (job) {
                case JobResult.Success<?> ignored -> succeeded++;
                case JobResult.Failure<?> failure -> {
                    failed++;
                    if (failure.dependencyFailure()) {
                        dependencyFailures++;
                    }
                }
                case JobResult.Cancelled<?> ignored -> cancelled++;
            }
        }
        return new ExecutionSummary(
                result.jobs().size(), succeeded, failed, cancelled, dependencyFailures, result.duration());
    }
}
