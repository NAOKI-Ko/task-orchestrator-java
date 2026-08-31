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
    Instant occurredAt();

    record JobStarted(JobId jobId, int attempt, Instant occurredAt) implements WorkflowEvent {
        public JobStarted {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    record JobCompleted(JobId jobId, int attempts, Duration duration, Instant occurredAt) implements WorkflowEvent {
        public JobCompleted {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(duration, "duration");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    record JobFailed(JobId jobId, String errorType, String message, Instant occurredAt) implements WorkflowEvent {
        public JobFailed {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(errorType, "errorType");
            Objects.requireNonNull(message, "message");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    record JobRetried(JobId jobId, int nextAttempt, Duration delay, Instant occurredAt) implements WorkflowEvent {
        public JobRetried {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(delay, "delay");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    record WorkflowCompleted(int jobCount, Duration duration, Instant occurredAt) implements WorkflowEvent {
        public WorkflowCompleted {
            Objects.requireNonNull(duration, "duration");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    record WorkflowFailed(int failedJobs, Duration duration, Instant occurredAt) implements WorkflowEvent {
        public WorkflowFailed {
            Objects.requireNonNull(duration, "duration");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }
}

