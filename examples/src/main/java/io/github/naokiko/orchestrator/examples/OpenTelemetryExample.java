package io.github.naokiko.orchestrator.examples;

import io.github.naokiko.orchestrator.OrchestratorTelemetry;
import io.github.naokiko.orchestrator.WorkflowBuilder;
import io.github.naokiko.orchestrator.WorkflowEngine;
import io.opentelemetry.api.OpenTelemetry;

public final class OpenTelemetryExample {
    private OpenTelemetryExample() {
        // Utility class.
    }

    public static void main(String[] arguments) {
        // Replace noop() with the application's configured OpenTelemetrySdk or GlobalOpenTelemetry.get().
        var openTelemetry = OpenTelemetry.noop();
        var workflow = WorkflowBuilder.create()
                .job("fetch", ignored -> "payload").done()
                .job("persist", ignored -> "stored").dependsOn("fetch").done()
                .build();

        try (var telemetry = OrchestratorTelemetry.create(openTelemetry);
                var engine = new WorkflowEngine(2, telemetry.eventSink(), telemetry.metrics())) {
            System.out.println("succeeded=" + engine.execute(workflow).join().succeeded());
        }
    }
}
