package io.github.naokiko.orchestrator;

import java.io.IOException;
import java.util.List;

/** Replaceable append-only workflow event persistence. */
public interface EventStore extends EventSink {
    @Override
    void publish(WorkflowEvent event);

    /**
     * Reads the persisted event records in append order.
     *
     * @return immutable snapshot of serialized event records
     * @throws IOException when the event log cannot be read
     */
    List<String> replay() throws IOException;
}
