package io.github.naokiko.orchestrator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fluent, reusable builder for immutable workflow definitions.
 *
 * <p>The builder preserves job insertion order while {@link WorkflowDefinition} remains
 * responsible for graph validation and deterministic planning. Calling {@link #build()} creates a
 * snapshot; jobs added later do not change previously built workflows. Instances are not
 * thread-safe.
 */
public final class WorkflowBuilder {
    private final Map<JobId, JobDefinition<?>> definitions = new LinkedHashMap<>();

    private WorkflowBuilder() { }

    /**
     * Creates an empty workflow builder.
     *
     * @return new reusable builder
     */
    public static WorkflowBuilder create() {
        return new WorkflowBuilder();
    }

    /**
     * Starts a typed job definition from a string identity.
     *
     * @param id non-blank workflow-local identity
     * @param action executable job body
     * @param <T> result type produced by the job
     * @return mutable job step that must be completed with {@link JobBuilder#done()}
     */
    public <T> JobBuilder<T> job(String id, JobAction<T> action) {
        return job(new JobId(id), action);
    }

    /**
     * Starts a typed job definition from an existing identity.
     *
     * @param id workflow-local identity
     * @param action executable job body
     * @param <T> result type produced by the job
     * @return mutable job step that must be completed with {@link JobBuilder#done()}
     */
    public <T> JobBuilder<T> job(JobId id, JobAction<T> action) {
        return new JobBuilder<>(this, Objects.requireNonNull(id, "id"), Objects.requireNonNull(action, "action"));
    }

    /**
     * Builds an immutable workflow snapshot and performs complete graph validation.
     *
     * @return validated workflow containing every completed job step
     * @throws IllegalArgumentException when dependencies are unknown or cyclic
     */
    public WorkflowDefinition build() {
        return new WorkflowDefinition(new ArrayList<>(definitions.values()));
    }

    void register(JobDefinition<?> definition) {
        if (definitions.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalArgumentException("duplicate job id: " + definition.id());
        }
    }
}
