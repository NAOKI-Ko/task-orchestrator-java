package io.github.naokiko.orchestrator;

import io.opentelemetry.api.OpenTelemetry;
import java.util.Objects;

/** Factory-owned pair of OpenTelemetry adapters for one workflow engine. */
public final class OrchestratorTelemetry implements AutoCloseable {
    private final OpenTelemetryEventSink eventSink;
    private final OpenTelemetryMetrics metrics;

    private OrchestratorTelemetry(OpenTelemetry openTelemetry) {
        this.eventSink = new OpenTelemetryEventSink(openTelemetry);
        this.metrics = new OpenTelemetryMetrics(openTelemetry);
    }

    /**
     * Creates tracing and metrics adapters from a configured OpenTelemetry API.
     *
     * @param openTelemetry configured API instance; the application owns its SDK lifecycle
     * @return paired adapters for one workflow engine
     */
    public static OrchestratorTelemetry create(OpenTelemetry openTelemetry) {
        return new OrchestratorTelemetry(Objects.requireNonNull(openTelemetry, "openTelemetry"));
    }

    /**
     * Returns the lifecycle-event tracing adapter.
     *
     * @return OpenTelemetry event sink
     */
    public EventSink eventSink() {
        return eventSink;
    }

    /**
     * Returns the engine metrics adapter.
     *
     * @return OpenTelemetry metrics adapter
     */
    public Metrics metrics() {
        return metrics;
    }

    /** Ends incomplete spans; the application remains responsible for closing its SDK. */
    @Override
    public void close() {
        eventSink.close();
    }
}
