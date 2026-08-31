package io.github.naokiko.orchestrator;

/** Checked functional job contract. */
@FunctionalInterface
public interface JobAction<T> {
    T execute(JobContext context) throws Exception;
}

