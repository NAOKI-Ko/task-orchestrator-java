package io.github.naokiko.orchestrator;

/**
 * Stable workflow-local identity.
 *
 * @param value non-blank identifier used for ordering, events, and metrics
 */
public record JobId(String value) implements Comparable<JobId> {
    /** Validates that the identifier is non-null and non-blank. */
    public JobId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("job id must not be blank");
        }
    }

    @Override
    public int compareTo(JobId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
