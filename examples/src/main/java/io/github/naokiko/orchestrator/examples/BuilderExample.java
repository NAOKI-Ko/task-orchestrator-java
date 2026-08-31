package io.github.naokiko.orchestrator.examples;

import io.github.naokiko.orchestrator.EventSink;
import io.github.naokiko.orchestrator.Metrics;
import io.github.naokiko.orchestrator.RetryPolicies;
import io.github.naokiko.orchestrator.WorkflowBuilder;
import io.github.naokiko.orchestrator.WorkflowEngine;
import java.time.Duration;

public final class BuilderExample {
    private BuilderExample() {
        // Utility class.
    }

    public static void main(String[] arguments) {
        var workflow = WorkflowBuilder.create()
                .job("fetch", context -> "payload")
                    .done()
                .job("transform", context -> "transformed")
                    .dependsOn("fetch")
                    .priority(10)
                    .timeout(Duration.ofSeconds(5))
                    .retry(RetryPolicies.exponentialWithJitter(
                            3, Duration.ofMillis(100), Duration.ofSeconds(2), 0.1))
                    .done()
                .job("persist", context -> "stored")
                    .dependsOn("transform")
                    .done()
                .build();

        try (var engine = new WorkflowEngine(4, EventSink.noop(), Metrics.noop())) {
            var result = engine.execute(workflow).join();
            System.out.println("succeeded=" + result.succeeded() + " jobs=" + result.jobs().size());
        }
    }
}
