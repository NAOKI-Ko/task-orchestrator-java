package io.github.naokiko.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenTelemetryIntegrationTest {
    private InMemorySpanExporter spanExporter;
    private InMemoryMetricReader metricReader;
    private SdkTracerProvider tracerProvider;
    private SdkMeterProvider meterProvider;
    private OpenTelemetrySdk openTelemetry;
    private OrchestratorTelemetry telemetry;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        metricReader = InMemoryMetricReader.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(metricReader)
                .build();
        openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setMeterProvider(meterProvider)
                .build();
        telemetry = OrchestratorTelemetry.create(openTelemetry);
    }

    @AfterEach
    void tearDown() {
        telemetry.close();
        meterProvider.close();
        tracerProvider.close();
    }

    @Test
    void exportsConcurrentChildSpansAndSemanticMetricsWithoutPayloads() {
        var secretPayload = "secret-customer-payload";
        var active = new AtomicInteger();
        var maximum = new AtomicInteger();
        var workflow = WorkflowBuilder.create()
                .job("fetch", ignored -> concurrentValue(secretPayload, active, maximum)).done()
                .job("transform", ignored -> concurrentValue("transformed", active, maximum)).done()
                .job("persist", ignored -> concurrentValue("stored", active, maximum)).done()
                .build();

        try (var engine = new WorkflowEngine(3, telemetry.eventSink(), telemetry.metrics())) {
            assertThat(engine.execute(workflow).join().succeeded()).isTrue();
        }

        var spans = spanExporter.getFinishedSpanItems();
        var workflowSpan = onlySpanNamed(spans, OpenTelemetryEventSink.WORKFLOW_SPAN_NAME);
        var jobSpans = spans.stream()
                .filter(span -> span.getName().equals(OpenTelemetryEventSink.JOB_SPAN_NAME))
                .toList();
        assertThat(jobSpans).hasSize(3).allSatisfy(span -> {
            assertThat(span.getParentSpanId()).isEqualTo(workflowSpan.getSpanId());
            assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
            assertThat(span.getAttributes().get(TelemetryAttributes.JOB_ID)).isNotBlank();
            assertThat(span.getAttributes().get(TelemetryAttributes.JOB_RESULT)).isEqualTo("success");
        });
        assertThat(workflowSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
        assertThat(workflowSpan.getAttributes().get(TelemetryAttributes.WORKFLOW_RESULT)).isEqualTo("success");
        assertThat(workflowSpan.getAttributes().get(TelemetryAttributes.WORKFLOW_JOB_COUNT)).isEqualTo(3L);
        assertThat(maximum).hasValueGreaterThan(1);
        assertThat(exportedSpanData(spans)).doesNotContain(secretPayload);

        var metrics = metricReader.collectAllMetrics();
        var eventMetric = metrics.stream()
                .filter(metric -> metric.getName().equals(OpenTelemetryMetrics.JOB_EVENTS_METRIC))
                .findFirst()
                .orElseThrow();
        var completedCount = eventMetric.getLongSumData().getPoints().stream()
                .filter(point -> "job.completed".equals(
                        point.getAttributes().get(TelemetryAttributes.METRIC_NAME)))
                .mapToLong(point -> point.getValue())
                .sum();
        assertThat(completedCount).isEqualTo(3);

        var durationMetric = metrics.stream()
                .filter(metric -> metric.getName().equals(OpenTelemetryMetrics.JOB_DURATION_METRIC))
                .findFirst()
                .orElseThrow();
        assertThat(durationMetric.getUnit()).isEqualTo("s");
        assertThat(durationMetric.getHistogramData().getPoints())
                .hasSize(3)
                .allSatisfy(point -> assertThat(point.getSum()).isPositive());
    }

    @Test
    void recordsFailureExceptionAndDependencyFailureWithoutErrorMessage() {
        var secretMessage = "secret-from-downstream";
        var workflow = WorkflowBuilder.create()
                .job("fail", ignored -> {
                    throw new IllegalArgumentException(secretMessage);
                }).done()
                .job("skipped", ignored -> "must-not-run").dependsOn("fail").done()
                .build();

        try (var engine = new WorkflowEngine(2, telemetry.eventSink(), telemetry.metrics())) {
            assertThat(engine.execute(workflow).join().succeeded()).isFalse();
        }

        var spans = spanExporter.getFinishedSpanItems();
        var workflowSpan = onlySpanNamed(spans, OpenTelemetryEventSink.WORKFLOW_SPAN_NAME);
        assertThat(workflowSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(workflowSpan.getAttributes().get(TelemetryAttributes.WORKFLOW_RESULT)).isEqualTo("failure");

        var failed = jobSpan(spans, "fail");
        assertThat(failed.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(failed.getEvents()).anySatisfy(event -> {
            assertThat(event.getName()).isEqualTo("exception");
            assertThat(event.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey(
                    "exception.type"))).isEqualTo("IllegalArgumentException");
        });
        var skipped = jobSpan(spans, "skipped");
        assertThat(skipped.getAttributes().get(TelemetryAttributes.JOB_DEPENDENCY_FAILURE)).isTrue();
        assertThat(skipped.getAttributes().get(TelemetryAttributes.JOB_DEPENDENCY_ID)).isEqualTo("fail");
        assertThat(exportedSpanData(spans)).doesNotContain(secretMessage, "must-not-run");
    }

    @Test
    void recordsRetryAndTimeoutEventsOnOneJobSpan() {
        var workflow = WorkflowBuilder.create()
                .job("slow", ignored -> {
                    Thread.sleep(Duration.ofSeconds(1));
                    return "late";
                })
                .timeout(Duration.ofMillis(10))
                .retry(RetryPolicies.fixed(2, Duration.ZERO))
                .done()
                .build();

        try (var engine = new WorkflowEngine(1, telemetry.eventSink(), telemetry.metrics())) {
            assertThat(engine.execute(workflow).join().succeeded()).isFalse();
        }

        var jobSpan = jobSpan(spanExporter.getFinishedSpanItems(), "slow");
        assertThat(jobSpan.getEvents()).extracting(event -> event.getName())
                .contains("job.retry", "job.timeout", "exception");
        assertThat(jobSpan.getEvents().stream().filter(event -> event.getName().equals("job.timeout")))
                .hasSize(2);
        assertThat(jobSpan.getAttributes().get(TelemetryAttributes.JOB_ATTEMPT)).isEqualTo(2L);
        assertThat(jobSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    }

    @Test
    void recordsCancellationEventAndStatus() throws Exception {
        var entered = new CountDownLatch(1);
        var workflow = WorkflowBuilder.create()
                .job("loop", context -> {
                    entered.countDown();
                    while (true) {
                        context.throwIfCancelled();
                        Thread.sleep(Duration.ofMillis(1));
                    }
                }).done()
                .build();

        try (var engine = new WorkflowEngine(1, telemetry.eventSink(), telemetry.metrics())) {
            var result = engine.execute(workflow);
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            engine.cancel();
            assertThat(result.join().succeeded()).isFalse();
        }

        var spans = spanExporter.getFinishedSpanItems();
        var cancelled = jobSpan(spans, "loop");
        assertThat(cancelled.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(cancelled.getAttributes().get(TelemetryAttributes.JOB_RESULT)).isEqualTo("cancelled");
        assertThat(cancelled.getEvents()).extracting(event -> event.getName()).contains("job.cancelled");
        assertThat(onlySpanNamed(spans, OpenTelemetryEventSink.WORKFLOW_SPAN_NAME)
                .getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    }

    @Test
    void validatesAdapterInputsAndClosesIncompleteSpans() {
        assertThatThrownBy(() -> OrchestratorTelemetry.create(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new OpenTelemetryEventSink(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new OpenTelemetryMetrics(null))
                .isInstanceOf(NullPointerException.class);

        var sink = new OpenTelemetryEventSink(openTelemetry);
        sink.publish(new WorkflowEvent.WorkflowStarted(1, Instant.EPOCH));
        sink.publish(new WorkflowEvent.JobStarted(new JobId("unfinished"), 1, Instant.EPOCH));
        assertThatThrownBy(() -> sink.publish(null)).isInstanceOf(NullPointerException.class);
        sink.close();
        sink.close();
        assertThatThrownBy(() -> sink.publish(new WorkflowEvent.WorkflowCompleted(
                        1, Duration.ZERO, Instant.EPOCH)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
        assertThat(jobSpan(spanExporter.getFinishedSpanItems(), "unfinished")
                .getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);

        var metrics = new OpenTelemetryMetrics(openTelemetry);
        assertThatThrownBy(() -> metrics.increment(null, new JobId("job")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> metrics.increment("count", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> metrics.recordNanos("duration", -1, new JobId("job")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> metrics.recordNanos(null, 1, new JobId("job")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> metrics.recordNanos("duration", 1, null))
                .isInstanceOf(NullPointerException.class);
    }

    private static String concurrentValue(String value, AtomicInteger active, AtomicInteger maximum)
            throws InterruptedException {
        var current = active.incrementAndGet();
        maximum.accumulateAndGet(current, Math::max);
        Thread.sleep(Duration.ofMillis(20));
        active.decrementAndGet();
        return value;
    }

    private static SpanData onlySpanNamed(List<SpanData> spans, String name) {
        return spans.stream().filter(span -> span.getName().equals(name)).findFirst().orElseThrow();
    }

    private static SpanData jobSpan(List<SpanData> spans, String jobId) {
        return spans.stream()
                .filter(span -> span.getName().equals(OpenTelemetryEventSink.JOB_SPAN_NAME))
                .filter(span -> jobId.equals(span.getAttributes().get(TelemetryAttributes.JOB_ID)))
                .findFirst()
                .orElseThrow();
    }

    private static String exportedSpanData(List<SpanData> spans) {
        return spans.stream()
                .map(span -> span.getAttributes().asMap() + " " + span.getEvents().stream()
                        .map(event -> event.getName() + event.getAttributes().asMap())
                        .toList())
                .toList()
                .toString();
    }
}
