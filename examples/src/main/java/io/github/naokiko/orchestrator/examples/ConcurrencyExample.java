package io.github.naokiko.orchestrator.examples;

import io.github.naokiko.orchestrator.EventSink;
import io.github.naokiko.orchestrator.JobDefinition;
import io.github.naokiko.orchestrator.JobId;
import io.github.naokiko.orchestrator.Metrics;
import io.github.naokiko.orchestrator.WorkflowDefinition;
import io.github.naokiko.orchestrator.WorkflowEngine;
import java.time.Duration;
import java.util.stream.IntStream;

public final class ConcurrencyExample {
    private ConcurrencyExample() {
        // Utility class.
    }

    public static void main(String[] arguments) {
        var jobs = IntStream.range(0, 8)
                .mapToObj(index -> JobDefinition.of(new JobId("parallel-" + index), ignored -> {
                    Thread.sleep(Duration.ofMillis(20));
                    return index;
                }))
                .toList();
        try (var engine = new WorkflowEngine(3, EventSink.noop(), Metrics.noop())) {
            var result = engine.execute(new WorkflowDefinition(jobs)).join();
            System.out.println("jobs=" + result.jobs().size() + " succeeded=" + result.succeeded());
        }
    }
}
