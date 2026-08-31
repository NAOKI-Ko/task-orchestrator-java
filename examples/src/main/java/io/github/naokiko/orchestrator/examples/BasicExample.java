package io.github.naokiko.orchestrator.examples;

import io.github.naokiko.orchestrator.AppendOnlyEventStore;
import io.github.naokiko.orchestrator.InMemoryMetrics;
import io.github.naokiko.orchestrator.JobDefinition;
import io.github.naokiko.orchestrator.JobId;
import io.github.naokiko.orchestrator.WorkflowDefinition;
import io.github.naokiko.orchestrator.WorkflowEngine;
import java.nio.file.Files;
import java.util.List;

public final class BasicExample {
    private BasicExample() {
        // Utility class.
    }

    public static void main(String[] arguments) throws Exception {
        var eventLog = Files.createTempFile("orchestrator", ".jsonl");
        eventLog.toFile().deleteOnExit();
        var download = JobDefinition.of(new JobId("download"), ignored -> "payload");
        try (var engine = new WorkflowEngine(4, new AppendOnlyEventStore(eventLog), new InMemoryMetrics())) {
            var result = engine.execute(new WorkflowDefinition(List.of(download))).join();
            System.out.println("succeeded=" + result.succeeded());
        }
    }
}
