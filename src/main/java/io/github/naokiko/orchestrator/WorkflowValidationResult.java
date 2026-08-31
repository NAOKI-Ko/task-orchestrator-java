package io.github.naokiko.orchestrator;

import java.util.List;
import java.util.Objects;

/**
 * Immutable result of non-throwing workflow pre-validation.
 *
 * @param valid whether validation succeeded
 * @param errors validation messages; empty exactly when valid
 */
public record WorkflowValidationResult(boolean valid, List<String> errors) {
    /** Defensively copies messages and enforces valid/error consistency. */
    public WorkflowValidationResult {
        errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
        if (valid == !errors.isEmpty()) {
            throw new IllegalArgumentException("valid workflows must have no errors");
        }
    }

    /**
     * Converts an invalid result back to the domain's exception-based contract.
     *
     * @throws IllegalArgumentException containing validation messages when invalid
     */
    public void throwIfInvalid() {
        if (!valid) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
    }
}
