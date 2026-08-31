package io.github.naokiko.orchestrator;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Closed, type-safe event hierarchy. */
public sealed interface WorkflowEvent
        permits WorkflowEvent.JobStarted,
                WorkflowEvent.JobCompleted,
                WorkflowEvent.JobFailed,
                WorkflowEvent.JobRetried,
                WorkflowEvent.WorkflowCompleted,
                WorkflowEvent.WorkflowFailed {
    /**
     * Returns the wall-clock time at which the event was emitted.
     *
     * @return event occurrence time
     */
    Instant occurredAt();

    /**
     * Signals the start of a job attempt.
     *
     * @param jobId executing job identity
     * @param attempt one-based attempt number
     * @param occurredAt event occurrence time
     */
    record JobStarted(JobId jobId, int attempt, Instant occurredAt) implements WorkflowEvent {
        /** Validates the event identity and occurrence time. */
        public JobStarted {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    /**
     * Signals successful completion of a job.
     *
     * @param jobId completed job identity
     * @param attempts total attempts performed
     * @param duration total execution duration
     * @param occurredAt event occurrence time
     */
    record JobCompleted(JobId jobId, int attempts, Duration duration, Instant occurredAt) implements WorkflowEvent {
        /** Validates the event identity, duration, and occurrence time. */
        public JobCompleted {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(duration, "duration");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    /**
     * Signals a permanent or dependency-caused job failure.
     *
     * @param jobId failed job identity
     * @param errorType simple failure type name
     * @param message human-readable failure message
     * @param occurredAt event occurrence time
     */
    record JobFailed(JobId jobId, String errorType, String message, Instant occurredAt) implements WorkflowEvent {
        /** Validates all event fields. */
        public JobFailed {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(errorType, "errorType");
            Objects.requireNonNull(message, "message");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    /**
     * Signals that another job attempt was scheduled.
     *
     * @param jobId retried job identity
     * @param nextAttempt one-based number of the scheduled attempt
     * @param delay backoff before the next attempt
     * @param occurredAt event occurrence time
     */
    record JobRetried(JobId jobId, int nextAttempt, Duration delay, Instant occurredAt) implements WorkflowEvent {
        /** Validates the event identity, delay, and occurrence time. */
        public JobRetried {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(delay, "delay");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    /**
     * Signals successful completion of an entire workflow.
     *
     * @param jobCount number of completed jobs
     * @param duration total workflow execution duration
     * @param occurredAt event occurrence time
     */
    record WorkflowCompleted(int jobCount, Duration duration, Instant occurredAt) implements WorkflowEvent {
        /** Validates the duration and occurrence time. */
        public WorkflowCompleted {
            Objects.requireNonNull(duration, "duration");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    /**
     * Signals completion of a workflow containing unsuccessful jobs.
     *
     * @param failedJobs number of failed or cancelled jobs
     * @param duration total workflow execution duration
     * @param occurredAt event occurrence time
     */
    record WorkflowFailed(int failedJobs, Duration duration, Instant occurredAt) implements WorkflowEvent {
        /** Validates the duration and occurrence time. */
        public WorkflowFailed {
            Objects.requireNonNull(duration, "duration");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }
}
