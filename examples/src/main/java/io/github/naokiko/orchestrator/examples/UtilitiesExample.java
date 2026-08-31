package io.github.naokiko.orchestrator.examples;

import io.github.naokiko.orchestrator.EventSinks;
import io.github.naokiko.orchestrator.ExecutionSummary;
import io.github.naokiko.orchestrator.InMemoryMetrics;
import io.github.naokiko.orchestrator.RetryPolicies;
import io.github.naokiko.orchestrator.WorkflowBuilder;
import io.github.naokiko.orchestrator.WorkflowEngine;
import io.github.naokiko.orchestrator.WorkflowEvent;
import io.github.naokiko.orchestrator.WorkflowValidator;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;

public final class UtilitiesExample {
    private UtilitiesExample() {
        // Utility class.
    }

    public static void main(String[] arguments) {
        var allEvents = new CopyOnWriteArrayList<WorkflowEvent>();
        var completions = new CopyOnWriteArrayList<WorkflowEvent>();
        var events = EventSinks.fanOut(
                allEvents::add,
                EventSinks.filter(completions::add, WorkflowEvent.JobCompleted.class::isInstance));
        var workflow = WorkflowBuilder.create()
                .job("fetch", context -> "payload")
                    .retry(RetryPolicies.exponential(
                            3, Duration.ofMillis(10), Duration.ofMillis(100)))
                    .done()
                .job("persist", context -> "stored")
                    .dependsOn("fetch")
                    .done()
                .build();

        WorkflowValidator.validate(workflow.jobs().values()).throwIfInvalid();
        try (var engine = new WorkflowEngine(2, events, new InMemoryMetrics())) {
            var summary = ExecutionSummary.from(engine.execute(workflow).join());
            System.out.println("succeeded=" + summary.succeeded()
                    + " total=" + summary.total()
                    + " events=" + allEvents.size()
                    + " completions=" + completions.size());
        }
    }
}
