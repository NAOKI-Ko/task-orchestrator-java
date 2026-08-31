package io.github.naokiko.orchestrator;

import java.util.List;
import java.util.function.Predicate;

/** Factory methods for common event-sink compositions. */
public final class EventSinks {
    private EventSinks() { }

    /**
     * Creates an immutable, ordered, fail-fast fan-out sink.
     *
     * @param delegates sinks invoked in argument order
     * @return composite sink; no arguments produce a no-op sink
     */
    public static EventSink fanOut(EventSink... delegates) {
        return new CompositeEventSink(List.of(delegates));
    }

    /**
     * Creates a sink that forwards events accepted by a predicate.
     *
     * @param delegate sink that receives matching events
     * @param predicate event selection rule
     * @return filtering sink
     */
    public static EventSink filter(EventSink delegate, Predicate<WorkflowEvent> predicate) {
        return new FilteringEventSink(delegate, predicate);
    }
}
