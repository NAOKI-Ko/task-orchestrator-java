package io.github.naokiko.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InfrastructureTest {
    @TempDir
    java.nio.file.Path temporaryDirectory;

    @Test
    void circuitBreakerOpensHalfOpensAndCloses() throws Exception {
        var clock = new AtomicLong();
        var breaker = new CircuitBreaker(2, Duration.ofNanos(10), clock::get);
        for (int index = 0; index < 2; index++) {
            assertThatThrownBy(() -> breaker.execute(() -> {
                throw new IllegalStateException("transient");
            })).isInstanceOf(IllegalStateException.class);
        }
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> breaker.execute(() -> "rejected"))
                .isInstanceOf(CircuitBreaker.CircuitOpenException.class);
        clock.set(10);
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        assertThat(breaker.execute(() -> "recovered")).isEqualTo("recovered");
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThatThrownBy(() -> new CircuitBreaker(0, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void appendOnlyStorePersistsEscapedTypedEvents() throws Exception {
        var path = temporaryDirectory.resolve("events.jsonl");
        var store = new AppendOnlyEventStore(path);
        assertThat(store.replay()).isEmpty();
        store.publish(new WorkflowEvent.JobStarted(new JobId("a\"b"), 1, Instant.EPOCH));
        store.publish(new WorkflowEvent.WorkflowCompleted(1, Duration.ofMillis(1), Instant.EPOCH));
        assertThat(store.replay())
                .hasSize(2)
                .first()
                .asString()
                .contains("JobStarted", "a\\\"b", "attempt");
        assertThatThrownBy(() -> new AppendOnlyEventStore(temporaryDirectory).publish(
                        new WorkflowEvent.WorkflowFailed(1, Duration.ZERO, Instant.EPOCH)))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void serializerCoversEverySealedEventVariant() {
        var id = new JobId("job");
        var now = Instant.EPOCH;
        assertThat(AppendOnlyEventStore.serialize(new WorkflowEvent.JobCompleted(id, 1, Duration.ZERO, now)))
                .contains("JobCompleted");
        assertThat(AppendOnlyEventStore.serialize(new WorkflowEvent.JobFailed(id, "IO", "bad", now)))
                .contains("errorType");
        assertThat(AppendOnlyEventStore.serialize(new WorkflowEvent.JobRetried(id, 2, Duration.ZERO, now)))
                .contains("nextAttempt");
        assertThat(AppendOnlyEventStore.serialize(new WorkflowEvent.WorkflowFailed(2, Duration.ZERO, now)))
                .contains("failedJobs");
    }

    @Test
    void inMemoryMetricsAreThreadSafeValueObjects() {
        var metrics = new InMemoryMetrics();
        var id = new JobId("job");
        assertThat(metrics.count("completed", id)).isZero();
        metrics.increment("completed", id);
        metrics.recordNanos("duration", 42, id);
        assertThat(metrics.count("completed", id)).isOne();
        assertThat(metrics.timings("duration", id)).containsExactly(42L);
        assertThat(metrics.timings("missing", id)).isEmpty();
        Metrics.noop().increment("ignored", id);
        Metrics.noop().recordNanos("ignored", 1, id);
        EventSink.noop().publish(new WorkflowEvent.WorkflowCompleted(0, Duration.ZERO, Instant.EPOCH));
    }
}

