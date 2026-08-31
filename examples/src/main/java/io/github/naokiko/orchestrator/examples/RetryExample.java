package io.github.naokiko.orchestrator.examples;

import io.github.naokiko.orchestrator.JobDefinition;
import io.github.naokiko.orchestrator.JobId;
import io.github.naokiko.orchestrator.Metrics;
import io.github.naokiko.orchestrator.RetryPolicy;
import io.github.naokiko.orchestrator.WorkflowDefinition;
import io.github.naokiko.orchestrator.WorkflowEngine;
import io.github.naokiko.orchestrator.WorkflowEvent;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public final class RetryExample {
    private RetryExample() {
        // Utility class.
    }

    public static void main(String[] arguments) {
        var attempts = new AtomicInteger();
        var events = new CopyOnWriteArrayList<WorkflowEvent>();
        var flaky = new JobDefinition<>(
                new JobId("flaky-api"),
                Set.of(),
                0,
                Duration.ofSeconds(1),
                new RetryPolicy(3, Duration.ofMillis(20), Duration.ofMillis(100), 2, 0.1),
                ignored -> {
                    if (attempts.incrementAndGet() == 1) {
                        throw new java.io.IOException("simulated reset");
                    }
                    return "recovered";
                });
        try (var engine = new WorkflowEngine(2, events::add, Metrics.noop())) {
            var result = engine.execute(new WorkflowDefinition(List.of(flaky))).join();
            System.out.println("succeeded=" + result.succeeded() + " events=" + events.size());
        }
    }
}
