package io.github.naokiko.orchestrator;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Thread-safe event adapter that translates workflow lifecycle events into OpenTelemetry spans.
 *
 * <p>One instance may observe sequential workflows. It must not be shared by concurrently executing
 * engines because the engine's event protocol deliberately has no global workflow identifier.
 */
public final class OpenTelemetryEventSink implements EventSink, AutoCloseable {
    /** Name of the workflow root span. */
    public static final String WORKFLOW_SPAN_NAME = "workflow.execute";

    /** Name of each job child span. */
    public static final String JOB_SPAN_NAME = "workflow.job";

    private static final AttributeKey<String> EXCEPTION_TYPE = AttributeKey.stringKey("exception.type");
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private final Tracer tracer;
    private final Map<JobId, Span> jobSpans = new LinkedHashMap<>();
    private Span workflowSpan;
    private boolean closed;

    /**
     * Creates a tracing adapter from the supplied OpenTelemetry API.
     *
     * @param openTelemetry configured OpenTelemetry API instance
     */
    public OpenTelemetryEventSink(OpenTelemetry openTelemetry) {
        this.tracer = Objects.requireNonNull(openTelemetry, "openTelemetry")
                .getTracer("io.github.naoki-ko.task-orchestrator-java");
    }

    /**
     * Translates one event into span lifecycle changes or span events.
     *
     * @param event workflow event to observe
     * @throws IllegalStateException when the adapter has been closed
     */
    @Override
    public synchronized void publish(WorkflowEvent event) {
        Objects.requireNonNull(event, "event");
        if (closed) {
            throw new IllegalStateException("OpenTelemetry event sink is closed");
        }
        switch (event) {
            case WorkflowEvent.WorkflowStarted started -> startWorkflow(started.jobCount());
            case WorkflowEvent.JobStarted started -> onJobStarted(started);
            case WorkflowEvent.JobCompleted completed -> onJobCompleted(completed);
            case WorkflowEvent.JobFailed failed -> onJobFailed(failed);
            case WorkflowEvent.JobRetried retried -> onJobRetried(retried);
            case WorkflowEvent.JobTimedOut timedOut -> onJobTimedOut(timedOut);
            case WorkflowEvent.JobDependencyFailed failed -> onDependencyFailed(failed);
            case WorkflowEvent.JobCancelled cancelled -> onJobCancelled(cancelled);
            case WorkflowEvent.WorkflowCompleted completed -> onWorkflowCompleted(completed);
            case WorkflowEvent.WorkflowFailed failed -> onWorkflowFailed(failed);
        }
    }

    private void startWorkflow(int jobCount) {
        finishIncompleteWorkflow();
        workflowSpan = tracer.spanBuilder(WORKFLOW_SPAN_NAME)
                .setNoParent()
                .startSpan();
        workflowSpan.setAttribute(TelemetryAttributes.WORKFLOW_JOB_COUNT, (long) jobCount);
    }

    private void onJobStarted(WorkflowEvent.JobStarted event) {
        var span = jobSpan(event.jobId());
        span.setAttribute(TelemetryAttributes.JOB_ATTEMPT, (long) event.attempt());
        span.setAttribute(TelemetryAttributes.JOB_STATE, "running");
        span.addEvent("job.attempt", Attributes.of(
                TelemetryAttributes.JOB_ATTEMPT, (long) event.attempt()));
    }

    private void onJobRetried(WorkflowEvent.JobRetried event) {
        var span = jobSpan(event.jobId());
        span.setAttribute(TelemetryAttributes.JOB_STATE, "retrying");
        span.addEvent("job.retry", Attributes.of(
                TelemetryAttributes.JOB_ATTEMPT, (long) event.nextAttempt(),
                TelemetryAttributes.RETRY_DELAY_SECONDS, seconds(event.delay())));
    }

    private void onJobTimedOut(WorkflowEvent.JobTimedOut event) {
        var span = jobSpan(event.jobId());
        span.setAttribute(TelemetryAttributes.JOB_STATE, "timed_out");
        span.addEvent("job.timeout", Attributes.of(
                TelemetryAttributes.JOB_ATTEMPT, (long) event.attempt(),
                TelemetryAttributes.JOB_TIMEOUT_SECONDS, seconds(event.timeout())));
    }

    private void onDependencyFailed(WorkflowEvent.JobDependencyFailed event) {
        var span = jobSpan(event.jobId());
        span.setAttribute(TelemetryAttributes.JOB_STATE, "skipped");
        span.setAttribute(TelemetryAttributes.JOB_DEPENDENCY_FAILURE, true);
        span.setAttribute(TelemetryAttributes.JOB_DEPENDENCY_ID, event.dependencyId().value());
        span.addEvent("job.dependency_failure", Attributes.of(
                TelemetryAttributes.JOB_DEPENDENCY_ID, event.dependencyId().value()));
    }

    private void onJobCompleted(WorkflowEvent.JobCompleted event) {
        var span = jobSpan(event.jobId());
        span.setAttribute(TelemetryAttributes.JOB_ATTEMPT, (long) event.attempts());
        span.setAttribute(TelemetryAttributes.JOB_STATE, "succeeded");
        span.setAttribute(TelemetryAttributes.JOB_RESULT, "success");
        span.setStatus(StatusCode.OK);
        span.end();
        jobSpans.remove(event.jobId());
    }

    private void onJobFailed(WorkflowEvent.JobFailed event) {
        var span = jobSpan(event.jobId());
        span.setAttribute(TelemetryAttributes.JOB_STATE, "failed");
        span.setAttribute(TelemetryAttributes.JOB_RESULT, "failure");
        span.addEvent("exception", Attributes.of(EXCEPTION_TYPE, event.errorType()));
        span.setStatus(StatusCode.ERROR);
        span.end();
        jobSpans.remove(event.jobId());
    }

    private void onJobCancelled(WorkflowEvent.JobCancelled event) {
        var span = jobSpan(event.jobId());
        span.setAttribute(TelemetryAttributes.JOB_STATE, "cancelled");
        span.setAttribute(TelemetryAttributes.JOB_RESULT, "cancelled");
        span.addEvent("job.cancelled");
        span.setStatus(StatusCode.ERROR);
        span.end();
        jobSpans.remove(event.jobId());
    }

    private void onWorkflowCompleted(WorkflowEvent.WorkflowCompleted event) {
        var span = workflowSpan();
        span.setAttribute(TelemetryAttributes.WORKFLOW_JOB_COUNT, (long) event.jobCount());
        span.setAttribute(TelemetryAttributes.WORKFLOW_RESULT, "success");
        span.setStatus(StatusCode.OK);
        finishWorkflow(span);
    }

    private void onWorkflowFailed(WorkflowEvent.WorkflowFailed event) {
        var span = workflowSpan();
        span.setAttribute(TelemetryAttributes.WORKFLOW_RESULT, "failure");
        span.setStatus(StatusCode.ERROR);
        finishWorkflow(span);
    }

    private Span jobSpan(JobId jobId) {
        return jobSpans.computeIfAbsent(jobId, ignored -> tracer.spanBuilder(JOB_SPAN_NAME)
                .setParent(Context.root().with(workflowSpan()))
                .setAttribute(TelemetryAttributes.JOB_ID, jobId.value())
                .setAttribute(TelemetryAttributes.JOB_DEPENDENCY_FAILURE, false)
                .startSpan());
    }

    private Span workflowSpan() {
        if (workflowSpan == null) {
            startWorkflow(0);
        }
        return workflowSpan;
    }

    private void finishWorkflow(Span span) {
        finishJobSpans();
        span.end();
        workflowSpan = null;
    }

    private void finishIncompleteWorkflow() {
        if (workflowSpan == null) {
            return;
        }
        finishJobSpans();
        workflowSpan.setAttribute(TelemetryAttributes.WORKFLOW_RESULT, "incomplete");
        workflowSpan.setStatus(StatusCode.ERROR);
        workflowSpan.end();
        workflowSpan = null;
    }

    private void finishJobSpans() {
        for (var span : jobSpans.values()) {
            span.setAttribute(TelemetryAttributes.JOB_RESULT, "incomplete");
            span.setStatus(StatusCode.ERROR);
            span.end();
        }
        jobSpans.clear();
    }

    private static double seconds(Duration duration) {
        return duration.toSeconds() + duration.toNanosPart() / NANOS_PER_SECOND;
    }

    /** Ends any incomplete spans and prevents further event delivery. */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        finishIncompleteWorkflow();
    }
}
