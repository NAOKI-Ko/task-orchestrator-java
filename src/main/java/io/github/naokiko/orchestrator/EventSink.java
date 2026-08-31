package io.github.naokiko.orchestrator;

/** Synchronous event delivery port; adapters decide their durability semantics. */
@FunctionalInterface
public interface EventSink {
    void publish(WorkflowEvent event);

    static EventSink noop() {
        return ignored -> { };
    }
}

