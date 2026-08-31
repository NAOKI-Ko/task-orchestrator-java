package io.github.naokiko.orchestrator;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Exhaustive result algebra for a job. */
public sealed interface JobResult<T>
        permits JobResult.Success, JobResult.Failure, JobResult.Cancelled {
    JobId jobId();

    Duration duration();

    default boolean isSuccess() {
        return this instanceof Success<?>;
    }

    default Optional<T> value() {
        return switch (this) {
            case Success<T>(var ignored, var result, var ignoredDuration, var ignoredAttempts) ->
                    Optional.ofNullable(result);
            case Failure<T> ignored -> Optional.empty();
            case Cancelled<T> ignored -> Optional.empty();
        };
    }

    record Success<T>(JobId jobId, T result, Duration duration, int attempts) implements JobResult<T> {
        public Success {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(duration, "duration");
        }
    }

    record Failure<T>(JobId jobId, FailureInfo error, Duration duration, int attempts, boolean dependencyFailure)
            implements JobResult<T> {
        public Failure {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(error, "error");
            Objects.requireNonNull(duration, "duration");
        }
    }

    record FailureInfo(String type, String message) {
        public FailureInfo {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(message, "message");
        }

        public static FailureInfo from(Throwable error) {
            Objects.requireNonNull(error, "error");
            return new FailureInfo(error.getClass().getSimpleName(), Objects.toString(error.getMessage(), ""));
        }
    }

    record Cancelled<T>(JobId jobId, String reason, Duration duration) implements JobResult<T> {
        public Cancelled {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(duration, "duration");
        }
    }
}
