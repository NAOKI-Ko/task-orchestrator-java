package io.github.naokiko.orchestrator;

import java.io.IOException;
import java.util.List;

/** Replaceable append-only workflow event persistence. */
public interface EventStore extends EventSink {
    @Override
    void publish(WorkflowEvent event);

    List<String> replay() throws IOException;
}

