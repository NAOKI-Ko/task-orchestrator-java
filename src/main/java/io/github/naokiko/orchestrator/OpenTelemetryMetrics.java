package io.github.naokiko.orchestrator;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import java.util.Objects;

/** OpenTelemetry implementation of the engine's existing metrics port. */
public final class OpenTelemetryMetrics implements Metrics {
    /** Counter instrument containing all engine counter measurements. */
    public static final String JOB_EVENTS_METRIC = "task.orchestrator.job.events";

    /** Histogram instrument containing job durations converted to seconds. */
    public static final String JOB_DURATION_METRIC = "task.orchestrator.job.duration";

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private final LongCounter jobEvents;
    private final DoubleHistogram jobDuration;

    /**
     * Creates instruments from the supplied OpenTelemetry API.
     *
     * @param openTelemetry configured OpenTelemetry API instance
     */
    public OpenTelemetryMetrics(OpenTelemetry openTelemetry) {
        var meter = Objects.requireNonNull(openTelemetry, "openTelemetry")
                .getMeter("io.github.naoki-ko.task-orchestrator-java");
        this.jobEvents = meter.counterBuilder(JOB_EVENTS_METRIC)
                .setDescription("Task orchestrator job lifecycle measurements")
                .setUnit("{event}")
                .build();
        this.jobDuration = meter.histogramBuilder(JOB_DURATION_METRIC)
                .setDescription("Task orchestrator job execution duration")
                .setUnit("s")
                .build();
    }

    @Override
    public void increment(String name, JobId jobId) {
        jobEvents.add(1, io.opentelemetry.api.common.Attributes.of(
                TelemetryAttributes.METRIC_NAME, Objects.requireNonNull(name, "name"),
                TelemetryAttributes.JOB_ID, Objects.requireNonNull(jobId, "jobId").value()));
    }

    @Override
    public void recordNanos(String name, long nanos, JobId jobId) {
        if (nanos < 0) {
            throw new IllegalArgumentException("nanos must not be negative");
        }
        jobDuration.record(nanos / NANOS_PER_SECOND, io.opentelemetry.api.common.Attributes.of(
                TelemetryAttributes.METRIC_NAME, Objects.requireNonNull(name, "name"),
                TelemetryAttributes.JOB_ID, Objects.requireNonNull(jobId, "jobId").value()));
    }
}
