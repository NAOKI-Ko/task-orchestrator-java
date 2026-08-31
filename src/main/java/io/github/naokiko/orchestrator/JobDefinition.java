package io.github.naokiko.orchestrator;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, typed job registration.
 *
 * @param <T> value type produced by the job action
 * @param id workflow-local job identity
 * @param dependencies jobs that must complete successfully first
 * @param priority scheduling priority; higher values run first within a ready set
 * @param timeout maximum duration of each attempt
 * @param retryPolicy retry and backoff policy
 * @param action executable job body
 */
public record JobDefinition<T>(
        JobId id,
        Set<JobId> dependencies,
        int priority,
        Duration timeout,
        RetryPolicy retryPolicy,
        JobAction<T> action) {
    /** Validates and defensively copies a job definition. */
    public JobDefinition {
        Objects.requireNonNull(id, "id");
        dependencies = Set.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(retryPolicy, "retryPolicy");
        Objects.requireNonNull(action, "action");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (dependencies.contains(id)) {
            throw new IllegalArgumentException("a job cannot depend on itself");
        }
    }

    /**
     * Creates an independent job with default priority, timeout, and no retries.
     *
     * @param id workflow-local job identity
     * @param action executable job body
     * @param <T> value type produced by the action
     * @return job definition using the documented defaults
     */
    public static <T> JobDefinition<T> of(JobId id, JobAction<T> action) {
        return new JobDefinition<>(id, Set.of(), 0, Duration.ofSeconds(30), RetryPolicy.none(), action);
    }
}
