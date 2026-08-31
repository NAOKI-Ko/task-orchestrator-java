package io.github.naokiko.orchestrator;

/** Telemetry-neutral metrics port. */
public interface Metrics {
    void increment(String name, JobId jobId);

    void recordNanos(String name, long nanos, JobId jobId);

    static Metrics noop() {
        return new Metrics() {
            @Override
            public void increment(String name, JobId jobId) { }

            @Override
            public void recordNanos(String name, long nanos, JobId jobId) { }
        };
    }
}

