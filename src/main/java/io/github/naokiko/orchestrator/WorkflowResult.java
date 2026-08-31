package io.github.naokiko.orchestrator;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable aggregate result with pattern-matched status derivation.
 *
 * @param jobs result for every workflow job, keyed by identity
 * @param duration total workflow execution duration
 */
public record WorkflowResult(Map<JobId, JobResult<?>> jobs, Duration duration) {
    /** Defensively copies job results and validates the duration. */
    public WorkflowResult {
        jobs = Map.copyOf(Objects.requireNonNull(jobs, "jobs"));
        Objects.requireNonNull(duration, "duration");
    }

    /**
     * Reports whether every job completed successfully.
     *
     * @return {@code true} when all job results are successful
     */
    public boolean succeeded() {
        return jobs.values().stream().allMatch(JobResult::isSuccess);
    }

    /**
     * Counts failed and cancelled jobs.
     *
     * @return number of unsuccessful job results
     */
    public long failedJobs() {
        return jobs.values().stream().filter(result -> switch (result) {
            case JobResult.Failure<?> ignored -> true;
            case JobResult.Cancelled<?> ignored -> true;
            case JobResult.Success<?> ignored -> false;
        }).count();
    }
}
