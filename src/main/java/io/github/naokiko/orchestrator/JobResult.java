package io.github.naokiko.orchestrator;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Exhaustive result algebra for a job.
 *
 * @param <T> value type produced by the job
 */
public sealed interface JobResult<T>
        permits JobResult.Success, JobResult.Failure, JobResult.Cancelled {
    /**
     * Returns the job that produced this result.
     *
     * @return workflow-local job identity
     */
    JobId jobId();

    /**
     * Returns the measured execution duration represented by this result.
     *
     * @return non-null duration
     */
    Duration duration();

    /**
     * Reports whether this result is a success variant.
     *
     * @return {@code true} only for {@link Success}
     */
    default boolean isSuccess() {
        return this instanceof Success<?>;
    }

    /**
     * Returns the successful value when this result is a success variant.
     *
     * @return value for a non-null success result, otherwise empty
     */
    default Optional<T> value() {
        return switch (this) {
            case Success<T>(var ignored, var result, var ignoredDuration, var ignoredAttempts) ->
                    Optional.ofNullable(result);
            case Failure<T> ignored -> Optional.empty();
            case Cancelled<T> ignored -> Optional.empty();
        };
    }

    /**
     * Successful terminal result.
     *
     * @param <T> value type produced by the job
     * @param jobId completed job identity
     * @param result produced value; may be {@code null}
     * @param duration total execution duration across attempts
     * @param attempts number of attempts performed
     */
    record Success<T>(JobId jobId, T result, Duration duration, int attempts) implements JobResult<T> {
        /** Validates the successful result identity and duration. */
        public Success {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(duration, "duration");
        }
    }

    /**
     * Failed terminal result.
     *
     * @param <T> value type that the job would have produced
     * @param jobId failed job identity
     * @param error stable failure description
     * @param duration total execution duration across attempts
     * @param attempts number of attempts performed
     * @param dependencyFailure whether execution was skipped because a dependency failed
     */
    record Failure<T>(JobId jobId, FailureInfo error, Duration duration, int attempts, boolean dependencyFailure)
            implements JobResult<T> {
        /** Validates the failed result identity, error, and duration. */
        public Failure {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(error, "error");
            Objects.requireNonNull(duration, "duration");
        }
    }

    /**
     * Dependency-free failure details safe to retain beyond the exception lifetime.
     *
     * @param type simple exception type name
     * @param message exception message, or an empty string
     */
    record FailureInfo(String type, String message) {
        /** Validates that both failure fields are present. */
        public FailureInfo {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(message, "message");
        }

        /**
         * Converts a throwable into stable failure details.
         *
         * @param error throwable to describe
         * @return normalized failure details
         */
        public static FailureInfo from(Throwable error) {
            Objects.requireNonNull(error, "error");
            return new FailureInfo(error.getClass().getSimpleName(), Objects.toString(error.getMessage(), ""));
        }
    }

    /**
     * Cancelled terminal result.
     *
     * @param <T> value type that the job would have produced
     * @param jobId cancelled job identity
     * @param reason human-readable cancellation reason
     * @param duration execution duration before cancellation
     */
    record Cancelled<T>(JobId jobId, String reason, Duration duration) implements JobResult<T> {
        /** Validates the cancellation identity, reason, and duration. */
        public Cancelled {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(duration, "duration");
        }
    }
}
