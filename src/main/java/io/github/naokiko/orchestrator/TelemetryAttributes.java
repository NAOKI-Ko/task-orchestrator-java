package io.github.naokiko.orchestrator;

import io.opentelemetry.api.common.AttributeKey;

/** Stable OpenTelemetry attribute vocabulary emitted by the optional adapters. */
public final class TelemetryAttributes {
    /** Workflow job count. */
    public static final AttributeKey<Long> WORKFLOW_JOB_COUNT =
            AttributeKey.longKey("task.orchestrator.workflow.job_count");

    /** Workflow terminal result: {@code success} or {@code failure}. */
    public static final AttributeKey<String> WORKFLOW_RESULT =
            AttributeKey.stringKey("task.orchestrator.workflow.result");

    /** Workflow-local job identifier. */
    public static final AttributeKey<String> JOB_ID =
            AttributeKey.stringKey("task.orchestrator.job.id");

    /** One-based attempt number. */
    public static final AttributeKey<Long> JOB_ATTEMPT =
            AttributeKey.longKey("task.orchestrator.job.attempt");

    /** Current or terminal job state. */
    public static final AttributeKey<String> JOB_STATE =
            AttributeKey.stringKey("task.orchestrator.job.state");

    /** Job terminal result: {@code success}, {@code failure}, or {@code cancelled}. */
    public static final AttributeKey<String> JOB_RESULT =
            AttributeKey.stringKey("task.orchestrator.job.result");

    /** Whether a job was skipped because a dependency was unsuccessful. */
    public static final AttributeKey<Boolean> JOB_DEPENDENCY_FAILURE =
            AttributeKey.booleanKey("task.orchestrator.job.dependency_failure");

    /** Identity of an unsuccessful dependency. */
    public static final AttributeKey<String> JOB_DEPENDENCY_ID =
            AttributeKey.stringKey("task.orchestrator.job.dependency.id");

    /** Retry delay measured in seconds. */
    public static final AttributeKey<Double> RETRY_DELAY_SECONDS =
            AttributeKey.doubleKey("task.orchestrator.retry.delay");

    /** Configured attempt timeout measured in seconds. */
    public static final AttributeKey<Double> JOB_TIMEOUT_SECONDS =
            AttributeKey.doubleKey("task.orchestrator.job.timeout");

    /** Original metrics-port measurement name. */
    public static final AttributeKey<String> METRIC_NAME =
            AttributeKey.stringKey("task.orchestrator.metric.name");

    private TelemetryAttributes() { }
}
