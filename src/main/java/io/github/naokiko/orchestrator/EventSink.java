package io.github.naokiko.orchestrator;

/** Synchronous event delivery port; adapters decide their durability semantics. */
@FunctionalInterface
public interface EventSink {
    /**
     * Delivers one workflow event to the configured adapter.
     *
     * @param event event to deliver
     */
    void publish(WorkflowEvent event);

    /**
     * Returns an event sink that deliberately discards every event.
     *
     * @return no-op event sink
     */
    static EventSink noop() {
        return ignored -> { };
    }
}
