package io.github.naokiko.orchestrator;

/**
 * Checked functional job contract.
 *
 * @param <T> result type produced by the job
 */
@FunctionalInterface
public interface JobAction<T> {
    /**
     * Executes the job for one attempt.
     *
     * @param context current job identity, attempt, and cancellation view
     * @return job result value; {@code null} is permitted
     * @throws Exception when the attempt fails
     */
    T execute(JobContext context) throws Exception;
}
