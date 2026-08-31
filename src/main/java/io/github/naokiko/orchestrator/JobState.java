package io.github.naokiko.orchestrator;

/** Observable execution lifecycle. */
public enum JobState {
    PENDING,
    READY,
    RUNNING,
    RETRYING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    SKIPPED
}

