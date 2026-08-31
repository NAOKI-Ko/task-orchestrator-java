package io.github.naokiko.orchestrator;

import java.time.Duration;

/** Named factories for common immutable retry policies. */
public final class RetryPolicies {
    private RetryPolicies() { }

    /**
     * Returns a single-attempt policy without delay.
     *
     * @return no-retry policy
     */
    public static RetryPolicy none() {
        return RetryPolicy.none();
    }

    /**
     * Creates a fixed-delay retry policy.
     *
     * @param maxAttempts maximum attempts including initial execution
     * @param delay delay between attempts
     * @return fixed-delay policy without jitter
     */
    public static RetryPolicy fixed(int maxAttempts, Duration delay) {
        return new RetryPolicy(maxAttempts, delay, delay, 1, 0);
    }

    /**
     * Creates exponential backoff with multiplier two and no jitter.
     *
     * @param maxAttempts maximum attempts including initial execution
     * @param initialDelay delay before the first retry
     * @param maxDelay upper bound for every retry delay
     * @return exponential policy without jitter
     */
    public static RetryPolicy exponential(int maxAttempts, Duration initialDelay, Duration maxDelay) {
        return new RetryPolicy(maxAttempts, initialDelay, maxDelay, 2, 0);
    }

    /**
     * Creates exponential backoff with multiplier two and symmetric jitter.
     *
     * @param maxAttempts maximum attempts including initial execution
     * @param initialDelay delay before the first retry
     * @param maxDelay upper bound for every retry delay
     * @param jitter symmetric jitter ratio in the inclusive range {@code [0, 1]}
     * @return exponential policy with jitter
     */
    public static RetryPolicy exponentialWithJitter(
            int maxAttempts, Duration initialDelay, Duration maxDelay, double jitter) {
        return new RetryPolicy(maxAttempts, initialDelay, maxDelay, 2, jitter);
    }
}
