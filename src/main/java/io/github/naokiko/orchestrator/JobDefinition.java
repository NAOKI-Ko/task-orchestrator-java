package io.github.naokiko.orchestrator;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/** Immutable, typed job registration. */
public record JobDefinition<T>(
        JobId id,
        Set<JobId> dependencies,
        int priority,
        Duration timeout,
        RetryPolicy retryPolicy,
        JobAction<T> action) {
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

    public static <T> JobDefinition<T> of(JobId id, JobAction<T> action) {
        return new JobDefinition<>(id, Set.of(), 0, Duration.ofSeconds(30), RetryPolicy.none(), action);
    }
}

