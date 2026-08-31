package io.github.naokiko.orchestrator;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Non-throwing facade over {@link WorkflowDefinition}'s canonical DAG validation. */
public final class WorkflowValidator {
    private WorkflowValidator() { }

    /**
     * Validates definitions by constructing the canonical immutable workflow model.
     *
     * <p>The method deliberately reuses {@link WorkflowDefinition}; it does not duplicate graph
     * validation or planning rules. At most one canonical validation message is returned.
     *
     * @param definitions complete workflow job set
     * @return valid result or one domain validation message
     */
    public static WorkflowValidationResult validate(
            Collection<? extends JobDefinition<?>> definitions) {
        if (definitions == null) {
            return new WorkflowValidationResult(false, List.of("definitions must not be null"));
        }
        if (definitions.stream().anyMatch(Objects::isNull)) {
            return new WorkflowValidationResult(false, List.of("definitions must not contain null"));
        }
        try {
            new WorkflowDefinition(definitions);
            return new WorkflowValidationResult(true, List.of());
        } catch (IllegalArgumentException error) {
            return new WorkflowValidationResult(
                    false, List.of(Objects.toString(error.getMessage(), error.getClass().getSimpleName())));
        }
    }
}
