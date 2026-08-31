package io.github.naokiko.orchestrator;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Concurrent DAG engine backed by Java 25 virtual threads. */
public final class WorkflowEngine implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(WorkflowEngine.class.getName());

    private final ExecutorService executor;
    private final Semaphore permits;
    private final EventSink events;
    private final Metrics metrics;
    private final Duration shutdownTimeout;
    private final Map<JobId, JobStateMachine> states = new ConcurrentHashMap<>();
    private final AtomicBoolean cancellationRequested = new AtomicBoolean();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Creates an engine with a ten-second graceful-shutdown timeout.
     *
     * @param concurrencyLimit maximum number of concurrently executing job actions
     * @param events synchronous workflow event sink
     * @param metrics workflow metrics adapter
     */
    public WorkflowEngine(int concurrencyLimit, EventSink events, Metrics metrics) {
        this(concurrencyLimit, events, metrics, Duration.ofSeconds(10));
    }

    /**
     * Creates an engine with an explicit graceful-shutdown timeout.
     *
     * @param concurrencyLimit maximum number of concurrently executing job actions
     * @param events synchronous workflow event sink
     * @param metrics workflow metrics adapter
     * @param shutdownTimeout maximum wait for virtual-thread termination during close
     */
    public WorkflowEngine(int concurrencyLimit, EventSink events, Metrics metrics, Duration shutdownTimeout) {
        if (concurrencyLimit < 1 || shutdownTimeout.isZero() || shutdownTimeout.isNegative()) {
            throw new IllegalArgumentException("concurrency limit and shutdown timeout must be positive");
        }
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.permits = new Semaphore(concurrencyLimit, true);
        this.events = Objects.requireNonNull(events, "events");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.shutdownTimeout = shutdownTimeout;
    }

    /**
     * Starts one workflow asynchronously.
     *
     * @param workflow validated workflow definition to execute
     * @return future completed with an exhaustive result for every job
     * @throws IllegalStateException when the engine is closed or already executing a workflow
     */
    public CompletableFuture<WorkflowResult> execute(WorkflowDefinition workflow) {
        Objects.requireNonNull(workflow, "workflow");
        if (closed.get()) {
            throw new IllegalStateException("engine is closed");
        }
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("this engine already has an active workflow");
        }
        cancellationRequested.set(false);
        states.clear();
        workflow.jobs().keySet().forEach(id -> states.put(id, new JobStateMachine()));
        var started = System.nanoTime();
        var futures = new LinkedHashMap<JobId, CompletableFuture<JobResult<?>>>();
        emit(new WorkflowEvent.WorkflowStarted(workflow.jobs().size(), Instant.now()));

        for (var id : workflow.plan().ordered()) {
            var definition = workflow.jobs().get(id);
            var dependencies = definition.dependencies().stream()
                    .map(futures::get)
                    .toArray(CompletableFuture<?>[]::new);
            var dependencyGate = CompletableFuture.allOf(dependencies);
            CompletableFuture<JobResult<?>> future = dependencyGate.thenApplyAsync(ignored -> {
                var dependencyResult = dependencyFailure(definition, futures);
                return dependencyResult.isPresent() ? dependencyResult.get() : runJob(definition);
            }, executor);
            futures.put(id, future);
        }

        var allJobs = CompletableFuture.allOf(futures.values().toArray(CompletableFuture<?>[]::new));
        return allJobs.handleAsync((ignored, unexpected) -> {
            try {
                var results = new LinkedHashMap<JobId, JobResult<?>>();
                futures.forEach((id, future) -> results.put(id, future.join()));
                var duration = Duration.ofNanos(System.nanoTime() - started);
                var result = new WorkflowResult(results, duration);
                if (result.succeeded()) {
                    emit(new WorkflowEvent.WorkflowCompleted(results.size(), duration, Instant.now()));
                } else {
                    emit(new WorkflowEvent.WorkflowFailed((int) result.failedJobs(), duration, Instant.now()));
                }
                return result;
            } finally {
                running.set(false);
            }
        }, executor);
    }

    private Optional<JobResult<?>> dependencyFailure(
            JobDefinition<?> definition,
            Map<JobId, CompletableFuture<JobResult<?>>> futures) {
        var failed = definition.dependencies().stream()
                .map(futures::get)
                .map(CompletableFuture::join)
                .filter(result -> !result.isSuccess())
                .findFirst();
        if (failed.isEmpty()) {
            return Optional.empty();
        }
        states.get(definition.id()).transition(JobState.SKIPPED);
        var error = new IllegalStateException("dependency failed: " + failed.get().jobId());
        emit(new WorkflowEvent.JobDependencyFailed(definition.id(), failed.get().jobId(), Instant.now()));
        emit(new WorkflowEvent.JobFailed(
                definition.id(), error.getClass().getSimpleName(), error.getMessage(), Instant.now()));
        return Optional.of(new JobResult.Failure<>(
                definition.id(), JobResult.FailureInfo.from(error), Duration.ZERO, 0, true));
    }

    private <T> JobResult<T> runJob(JobDefinition<T> definition) {
        var machine = states.get(definition.id());
        machine.transition(JobState.READY);
        var started = System.nanoTime();
        if (cancellationRequested.get()) {
            machine.transition(JobState.CANCELLED);
            emit(new WorkflowEvent.JobCancelled(definition.id(), Instant.now()));
            return new JobResult.Cancelled<>(definition.id(), "cancelled before execution", Duration.ZERO);
        }

        Exception lastFailure = new IllegalStateException("job did not execute");
        var breaker = new CircuitBreaker(Math.max(2, definition.retryPolicy().maxAttempts()), Duration.ofSeconds(1));
        for (int attempt = 1; attempt <= definition.retryPolicy().maxAttempts(); attempt++) {
            machine.transition(JobState.RUNNING);
            emit(new WorkflowEvent.JobStarted(definition.id(), attempt, Instant.now()));
            try {
                var value = invokeWithTimeout(definition, attempt, breaker);
                machine.transition(JobState.SUCCEEDED);
                var duration = Duration.ofNanos(System.nanoTime() - started);
                metrics.increment("job.completed", definition.id());
                metrics.recordNanos("job.duration", duration.toNanos(), definition.id());
                emit(new WorkflowEvent.JobCompleted(definition.id(), attempt, duration, Instant.now()));
                LOGGER.log(System.Logger.Level.INFO,
                        "event=job_completed job_id={0} attempts={1} duration_nanos={2}",
                        definition.id(), attempt, duration.toNanos());
                return new JobResult.Success<>(definition.id(), value, duration, attempt);
            } catch (CancellationException error) {
                machine.transition(JobState.CANCELLED);
                emit(new WorkflowEvent.JobCancelled(definition.id(), Instant.now()));
                return new JobResult.Cancelled<>(
                        definition.id(), error.getMessage(), Duration.ofNanos(System.nanoTime() - started));
            } catch (Exception error) {
                lastFailure = error;
                if (error instanceof TimeoutException) {
                    emit(new WorkflowEvent.JobTimedOut(
                            definition.id(), attempt, definition.timeout(), Instant.now()));
                }
                if (attempt < definition.retryPolicy().maxAttempts()) {
                    machine.transition(JobState.RETRYING);
                    var delay = definition.retryPolicy().delayFor(attempt, ThreadLocalRandom.current().nextDouble());
                    metrics.increment("job.retried", definition.id());
                    emit(new WorkflowEvent.JobRetried(definition.id(), attempt + 1, delay, Instant.now()));
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        cancellationRequested.set(true);
                        machine.transition(JobState.CANCELLED);
                        emit(new WorkflowEvent.JobCancelled(definition.id(), Instant.now()));
                        return new JobResult.Cancelled<>(
                                definition.id(), "retry interrupted", Duration.ofNanos(System.nanoTime() - started));
                    }
                }
            }
        }

        machine.transition(JobState.FAILED);
        var duration = Duration.ofNanos(System.nanoTime() - started);
        metrics.increment("job.failed", definition.id());
        metrics.recordNanos("job.duration", duration.toNanos(), definition.id());
        emit(new WorkflowEvent.JobFailed(
                definition.id(),
                lastFailure.getClass().getSimpleName(),
                Objects.toString(lastFailure.getMessage(), ""),
                Instant.now()));
        return new JobResult.Failure<>(
                definition.id(),
                JobResult.FailureInfo.from(lastFailure),
                duration,
                definition.retryPolicy().maxAttempts(),
                false);
    }

    private <T> T invokeWithTimeout(JobDefinition<T> definition, int attempt, CircuitBreaker breaker)
            throws Exception {
        if (cancellationRequested.get()) {
            throw new CancellationException("workflow cancelled");
        }
        permits.acquire();
        Future<T> future = executor.submit(() -> breaker.execute(() -> definition.action().execute(
                new JobContext(definition.id(), attempt, cancellationRequested::get))));
        try {
            return future.get(definition.timeout().toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException error) {
            future.cancel(true);
            throw error;
        } catch (ExecutionException error) {
            var cause = error.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new IllegalStateException("job failed with an unrecoverable error", cause);
        } catch (InterruptedException error) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new CancellationException("job interrupted");
        } finally {
            permits.release();
        }
    }

    private void emit(WorkflowEvent event) {
        events.publish(event);
    }

    /**
     * Returns the current lifecycle state of a registered job in the active or latest workflow.
     *
     * @param jobId workflow-local job identity
     * @return current job state
     * @throws IllegalArgumentException when the job is unknown to this engine
     */
    public JobState stateOf(JobId jobId) {
        var machine = states.get(jobId);
        if (machine == null) {
            throw new IllegalArgumentException("unknown job: " + jobId);
        }
        return machine.state();
    }

    /** Requests cooperative cancellation of the active workflow. */
    public void cancel() {
        cancellationRequested.set(true);
    }

    /** Stops accepting work, requests cancellation, and shuts down virtual threads. */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        cancel();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(shutdownTimeout.toNanos(), TimeUnit.NANOSECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException error) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
