package io.github.naokiko.orchestrator;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Fluent configuration step for one typed job.
 *
 * <p>Defaults match {@link JobDefinition#of(JobId, JobAction)}: no dependencies, priority zero, a
 * 30-second timeout, and no retry. A step is single-use after {@link #done()} and is not
 * thread-safe.
 *
 * @param <T> result type produced by the job
 */
public final class JobBuilder<T> {
    private final WorkflowBuilder workflow;
    private final JobId id;
    private final JobAction<T> action;
    private final Set<JobId> dependencies = new LinkedHashSet<>();
    private int priority;
    private Duration timeout = Duration.ofSeconds(30);
    private RetryPolicy retryPolicy = RetryPolicy.none();
    private boolean completed;

    JobBuilder(WorkflowBuilder workflow, JobId id, JobAction<T> action) {
        this.workflow = workflow;
        this.id = id;
        this.action = action;
    }

    /**
     * Adds dependencies by identity.
     *
     * @param required jobs that must complete successfully before this job runs
     * @return this job step
     */
    public JobBuilder<T> dependsOn(JobId... required) {
        ensureOpen();
        Objects.requireNonNull(required, "required");
        for (var dependency : required) {
            dependencies.add(Objects.requireNonNull(dependency, "dependency"));
        }
        return this;
    }

    /**
     * Adds dependencies from non-blank string identities.
     *
     * @param required jobs that must complete successfully before this job runs
     * @return this job step
     */
    public JobBuilder<T> dependsOn(String... required) {
        ensureOpen();
        Objects.requireNonNull(required, "required");
        for (var dependency : required) {
            dependencies.add(new JobId(dependency));
        }
        return this;
    }

    /**
     * Sets scheduling priority among jobs ready at the same time.
     *
     * @param value priority value; higher values plan first
     * @return this job step
     */
    public JobBuilder<T> priority(int value) {
        ensureOpen();
        priority = value;
        return this;
    }

    /**
     * Sets the maximum duration of each execution attempt.
     *
     * @param value positive attempt timeout
     * @return this job step
     */
    public JobBuilder<T> timeout(Duration value) {
        ensureOpen();
        timeout = Objects.requireNonNull(value, "value");
        return this;
    }

    /**
     * Sets retry and backoff behavior.
     *
     * @param value retry policy
     * @return this job step
     */
    public JobBuilder<T> retry(RetryPolicy value) {
        ensureOpen();
        retryPolicy = Objects.requireNonNull(value, "value");
        return this;
    }

    /**
     * Validates and adds this job to its workflow builder.
     *
     * @return owning workflow builder for the next job or final build
     * @throws IllegalArgumentException for a duplicate identity, self dependency, or invalid timeout
     * @throws IllegalStateException when this step was already completed
     */
    public WorkflowBuilder done() {
        ensureOpen();
        var definition = new JobDefinition<>(id, dependencies, priority, timeout, retryPolicy, action);
        workflow.register(definition);
        completed = true;
        return workflow;
    }

    private void ensureOpen() {
        if (completed) {
            throw new IllegalStateException("job builder is already completed: " + id);
        }
    }
}
