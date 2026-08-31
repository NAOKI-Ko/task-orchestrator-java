package io.github.naokiko.orchestrator;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Lock-free state machine that rejects impossible lifecycle transitions. */
public final class JobStateMachine {
    private static final Map<JobState, Set<JobState>> TRANSITIONS = Map.of(
            JobState.PENDING, Set.of(JobState.READY, JobState.CANCELLED, JobState.SKIPPED),
            JobState.READY, Set.of(JobState.RUNNING, JobState.CANCELLED),
            JobState.RUNNING, Set.of(JobState.RETRYING, JobState.SUCCEEDED, JobState.FAILED, JobState.CANCELLED),
            JobState.RETRYING, Set.of(JobState.RUNNING, JobState.CANCELLED),
            JobState.SUCCEEDED, Set.of(),
            JobState.FAILED, Set.of(),
            JobState.CANCELLED, Set.of(),
            JobState.SKIPPED, Set.of());

    private final AtomicReference<JobState> state = new AtomicReference<>(JobState.PENDING);

    /** Creates a state machine in {@link JobState#PENDING}. */
    public JobStateMachine() { }

    /**
     * Returns the current lifecycle state.
     *
     * @return current state
     */
    public JobState state() {
        return state.get();
    }

    /**
     * Atomically advances to a permitted target state.
     *
     * @param target desired next state
     * @throws IllegalStateException when the transition is not permitted
     */
    public void transition(JobState target) {
        while (true) {
            var current = state.get();
            if (!TRANSITIONS.get(current).contains(target)) {
                throw new IllegalStateException("invalid job transition: " + current + " -> " + target);
            }
            if (state.compareAndSet(current, target)) {
                return;
            }
        }
    }
}
