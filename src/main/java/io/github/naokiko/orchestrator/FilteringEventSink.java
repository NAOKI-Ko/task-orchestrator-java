package io.github.naokiko.orchestrator;

import java.util.Objects;
import java.util.function.Predicate;

/** Event sink that forwards only events accepted by a predicate. */
public final class FilteringEventSink implements EventSink {
    private final EventSink delegate;
    private final Predicate<WorkflowEvent> predicate;

    /**
     * Creates a filtering adapter.
     *
     * @param delegate sink that receives matching events
     * @param predicate event selection rule
     */
    public FilteringEventSink(EventSink delegate, Predicate<WorkflowEvent> predicate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.predicate = Objects.requireNonNull(predicate, "predicate");
    }

    /**
     * Evaluates and conditionally forwards one event.
     *
     * <p>Predicate and delegate exceptions are propagated to the publisher.
     *
     * @param event event to evaluate
     */
    @Override
    public void publish(WorkflowEvent event) {
        Objects.requireNonNull(event, "event");
        if (predicate.test(event)) {
            delegate.publish(event);
        }
    }
}
