package io.github.naokiko.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WorkflowEngineTest {
    @Test
    void executesDependenciesAndReturnsTypedResults() {
        var events = new CopyOnWriteArrayList<WorkflowEvent>();
        var metrics = new InMemoryMetrics();
        var order = new CopyOnWriteArrayList<String>();
        var root = JobDefinition.of(new JobId("root"), ignored -> {
            order.add("root");
            return 20;
        });
        var child = new JobDefinition<>(
                new JobId("child"),
                Set.of(root.id()),
                0,
                Duration.ofSeconds(1),
                RetryPolicy.none(),
                ignored -> {
                    order.add("child");
                    return 22;
                });

        try (var engine = new WorkflowEngine(2, events::add, metrics)) {
            var result = engine.execute(new WorkflowDefinition(List.of(child, root))).join();
            assertThat(result.succeeded()).isTrue();
            assertThat(result.failedJobs()).isZero();
            assertThat(((JobResult.Success<?>) result.jobs().get(child.id())).result()).isEqualTo(22);
            assertThat(order).containsExactly("root", "child");
            assertThat(engine.stateOf(root.id())).isEqualTo(JobState.SUCCEEDED);
            assertThat(metrics.count("job.completed", child.id())).isOne();
            assertThat(metrics.timings("job.duration", root.id())).hasSize(1);
            assertThat(events).anyMatch(WorkflowEvent.WorkflowCompleted.class::isInstance);
        }
    }

    @Test
    void retriesTransientFailuresAndEmitsTypedEvents() {
        var invocations = new AtomicInteger();
        var events = new CopyOnWriteArrayList<WorkflowEvent>();
        var id = new JobId("flaky");
        var job = new JobDefinition<>(
                id,
                Set.of(),
                0,
                Duration.ofSeconds(1),
                new RetryPolicy(3, Duration.ZERO, Duration.ZERO, 1, 0),
                ignored -> {
                    if (invocations.incrementAndGet() < 3) {
                        throw new java.io.IOException("transient");
                    }
                    return "ok";
                });
        try (var engine = new WorkflowEngine(1, events::add, Metrics.noop())) {
            var result = engine.execute(new WorkflowDefinition(List.of(job))).join();
            assertThat(result.succeeded()).isTrue();
            assertThat(((JobResult.Success<?>) result.jobs().get(id)).attempts()).isEqualTo(3);
            assertThat(events.stream().filter(WorkflowEvent.JobRetried.class::isInstance)).hasSize(2);
        }
    }

    @Test
    void timeoutFailsJobAndSkipsDependents() {
        var timeoutId = new JobId("timeout");
        var dependentId = new JobId("dependent");
        var timeout = new JobDefinition<>(
                timeoutId,
                Set.of(),
                0,
                Duration.ofMillis(10),
                RetryPolicy.none(),
                ignored -> {
                    Thread.sleep(Duration.ofSeconds(2));
                    return null;
                });
        var dependent = new JobDefinition<>(
                dependentId,
                Set.of(timeoutId),
                0,
                Duration.ofSeconds(1),
                RetryPolicy.none(),
                ignored -> "unsafe");
        try (var engine = new WorkflowEngine(2, EventSink.noop(), Metrics.noop())) {
            var result = engine.execute(new WorkflowDefinition(List.of(timeout, dependent))).join();
            assertThat(result.succeeded()).isFalse();
            assertThat(result.failedJobs()).isEqualTo(2);
            assertThat(result.jobs().get(timeoutId)).isInstanceOf(JobResult.Failure.class);
            assertThat(((JobResult.Failure<?>) result.jobs().get(dependentId)).dependencyFailure()).isTrue();
            assertThat(engine.stateOf(dependentId)).isEqualTo(JobState.SKIPPED);
        }
    }

    @Test
    void semaphoreBoundsParallelJobBodies() {
        var active = new AtomicInteger();
        var maximum = new AtomicInteger();
        var jobs = java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> JobDefinition.of(new JobId("job-" + index), ignored -> {
                    var concurrent = active.incrementAndGet();
                    maximum.accumulateAndGet(concurrent, Math::max);
                    Thread.sleep(Duration.ofMillis(15));
                    active.decrementAndGet();
                    return index;
                }))
                .toList();
        try (var engine = new WorkflowEngine(2, EventSink.noop(), Metrics.noop())) {
            assertThat(engine.execute(new WorkflowDefinition(jobs)).join().succeeded()).isTrue();
            assertThat(maximum).hasValueBetween(1, 2);
        }
    }

    @Test
    void cooperativeCancellationProducesCancelledResult() throws Exception {
        var started = new CountDownLatch(1);
        var id = new JobId("loop");
        var job = JobDefinition.of(id, context -> {
            started.countDown();
            while (true) {
                context.throwIfCancelled();
                Thread.sleep(Duration.ofMillis(1));
            }
        });
        try (var engine = new WorkflowEngine(1, EventSink.noop(), Metrics.noop())) {
            var future = engine.execute(new WorkflowDefinition(List.of(job)));
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            engine.cancel();
            var result = future.join();
            assertThat(result.jobs().get(id)).isInstanceOf(JobResult.Cancelled.class);
            assertThat(engine.stateOf(id)).isEqualTo(JobState.CANCELLED);
        }
    }

    @Test
    void engineRejectsInvalidUsageAndIsIdempotentlyCloseable() throws Exception {
        assertThatThrownBy(() -> new WorkflowEngine(0, EventSink.noop(), Metrics.noop()))
                .isInstanceOf(IllegalArgumentException.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var job = JobDefinition.of(new JobId("blocked"), ignored -> {
            entered.countDown();
            release.await();
            return null;
        });
        var engine = new WorkflowEngine(1, EventSink.noop(), Metrics.noop());
        var workflow = new WorkflowDefinition(List.of(job));
        var future = engine.execute(workflow);
        assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> engine.execute(workflow))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active");
        assertThatThrownBy(() -> engine.stateOf(new JobId("missing")))
                .isInstanceOf(IllegalArgumentException.class);
        release.countDown();
        future.join();
        engine.close();
        engine.close();
        assertThatThrownBy(() -> engine.execute(workflow))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }
}
