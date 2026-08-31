package io.github.naokiko.orchestrator;

/** Observable execution lifecycle. */
public enum JobState {
    /** Registered but not yet dependency-ready. */
    PENDING,
    /** Dependencies succeeded and the job may start. */
    READY,
    /** The job action is executing. */
    RUNNING,
    /** A failed attempt is waiting for its retry delay. */
    RETRYING,
    /** The job completed successfully. */
    SUCCEEDED,
    /** The job exhausted retries or failed permanently. */
    FAILED,
    /** Workflow cancellation stopped the job. */
    CANCELLED,
    /** A dependency failure prevented execution. */
    SKIPPED
}
