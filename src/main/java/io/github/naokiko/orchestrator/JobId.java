package io.github.naokiko.orchestrator;

/** Stable workflow-local identity. */
public record JobId(String value) implements Comparable<JobId> {
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

