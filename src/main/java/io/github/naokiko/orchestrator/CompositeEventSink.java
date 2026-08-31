package io.github.naokiko.orchestrator;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Immutable event sink that delivers each event to multiple delegates in deterministic order.
 *
 * <p>An empty delegate collection is a no-op. Delivery is fail-fast: if a delegate throws, the
 * exception is propagated immediately and later delegates are not invoked for that event.
 */
public final class CompositeEventSink implements EventSink {
    private final List<EventSink> delegates;

    /**
     * Creates an ordered fan-out sink.
     *
     * @param delegates sinks invoked in collection iteration order
     */
    public CompositeEventSink(Collection<? extends EventSink> delegates) {
        Objects.requireNonNull(delegates, "delegates");
        this.delegates = List.copyOf(delegates);
    }

    /**
     * Delivers the event to each delegate until all succeed or one throws.
     *
     * @param event event to deliver
     */
    @Override
    public void publish(WorkflowEvent event) {
        Objects.requireNonNull(event, "event");
        for (var delegate : delegates) {
            delegate.publish(event);
        }
    }
}
