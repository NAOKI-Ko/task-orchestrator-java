package io.github.naokiko.orchestrator;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/** Immutable aggregate result with pattern-matched status derivation. */
public record WorkflowResult(Map<JobId, JobResult<?>> jobs, Duration duration) {
    public WorkflowResult {
        jobs = Map.copyOf(Objects.requireNonNull(jobs, "jobs"));
        Objects.requireNonNull(duration, "duration");
    }

    public boolean succeeded() {
        return jobs.values().stream().allMatch(JobResult::isSuccess);
    }

    public long failedJobs() {
        return jobs.values().stream().filter(result -> switch (result) {
            case JobResult.Failure<?> ignored -> true;
            case JobResult.Cancelled<?> ignored -> true;
            case JobResult.Success<?> ignored -> false;
        }).count();
    }
}

