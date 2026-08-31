package io.github.naokiko.orchestrator;

/** Telemetry-neutral metrics port. */
public interface Metrics {
    /**
     * Increments a named per-job counter.
     *
     * @param name metric name
     * @param jobId job associated with the measurement
     */
    void increment(String name, JobId jobId);

    /**
     * Records a nanosecond duration for a named per-job metric.
     *
     * @param name metric name
     * @param nanos measured duration in nanoseconds
     * @param jobId job associated with the measurement
     */
    void recordNanos(String name, long nanos, JobId jobId);

    /**
     * Returns a metrics adapter that deliberately discards all measurements.
     *
     * @return no-op metrics adapter
     */
    static Metrics noop() {
        return new Metrics() {
            @Override
            public void increment(String name, JobId jobId) { }

            @Override
            public void recordNanos(String name, long nanos, JobId jobId) { }
        };
    }
}
