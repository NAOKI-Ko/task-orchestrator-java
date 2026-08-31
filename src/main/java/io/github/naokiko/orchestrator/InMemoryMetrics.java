package io.github.naokiko.orchestrator;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.LongAdder;

/** Thread-safe metrics adapter useful in tests and embedded deployments. */
public final class InMemoryMetrics implements Metrics {
    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<Long>> timings = new ConcurrentHashMap<>();

    @Override
    public void increment(String name, JobId jobId) {
        counters.computeIfAbsent(key(name, jobId), ignored -> new LongAdder()).increment();
    }

    @Override
    public void recordNanos(String name, long nanos, JobId jobId) {
        timings.computeIfAbsent(key(name, jobId), ignored -> new CopyOnWriteArrayList<>()).add(nanos);
    }

    public long count(String name, JobId jobId) {
        var counter = counters.get(key(name, jobId));
        return counter == null ? 0 : counter.sum();
    }

    public List<Long> timings(String name, JobId jobId) {
        return List.copyOf(timings.getOrDefault(key(name, jobId), new CopyOnWriteArrayList<>()));
    }

    private static String key(String name, JobId jobId) {
        return name + ":" + jobId.value();
    }
}

