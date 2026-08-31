package io.github.naokiko.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

class DomainTest {
    @Test
    void jobIdentityIsValidatedAndOrdered() {
        assertThat(new JobId("a")).isLessThan(new JobId("b")).hasToString("a");
        assertThatThrownBy(() -> new JobId(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JobId(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Property
    void exponentialBackoffIsAlwaysBounded(
            @ForAll @IntRange(min = 1, max = 100) int attempt,
            @ForAll @DoubleRange(min = 0, max = 1) double randomValue) {
        var policy = new RetryPolicy(5, Duration.ofMillis(10), Duration.ofSeconds(2), 2, 0.5);
        var delay = policy.delayFor(attempt, randomValue);
        assertThat(delay.isNegative()).isFalse();
        assertThat(delay).isLessThanOrEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void policyAndDefinitionRejectInvalidValues() {
        assertThatThrownBy(() -> new RetryPolicy(0, Duration.ZERO, Duration.ZERO, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(1, Duration.ofMillis(-1), Duration.ZERO, 1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delays");
        assertThatThrownBy(() -> new RetryPolicy(1, Duration.ofSeconds(2), Duration.ofSeconds(1), 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(1, Duration.ZERO, Duration.ZERO, 0.5, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(1, Duration.ZERO, Duration.ZERO, 1, -0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jitter");
        assertThatThrownBy(() -> new RetryPolicy(1, Duration.ZERO, Duration.ZERO, 1, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JobDefinition<>(
                        new JobId("self"),
                        Set.of(new JobId("self")),
                        0,
                        Duration.ofSeconds(1),
                        RetryPolicy.none(),
                        ignored -> null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JobDefinition<>(
                        new JobId("zero"), Set.of(), 0, Duration.ZERO, RetryPolicy.none(), ignored -> null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JobDefinition<>(
                        new JobId("negative"),
                        Set.of(),
                        0,
                        Duration.ofMillis(-1),
                        RetryPolicy.none(),
                        ignored -> null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    void stateMachineOnlyAllowsDocumentedTransitions() {
        var machine = new JobStateMachine();
        machine.transition(JobState.READY);
        machine.transition(JobState.RUNNING);
        machine.transition(JobState.RETRYING);
        machine.transition(JobState.RUNNING);
        machine.transition(JobState.SUCCEEDED);
        assertThat(machine.state()).isEqualTo(JobState.SUCCEEDED);
        assertThatThrownBy(() -> machine.transition(JobState.RUNNING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUCCEEDED");
    }

    @Test
    void contextAndResultAlgebraExposeTypedValues() {
        var id = new JobId("job");
        var context = new JobContext(id, 1, () -> false);
        context.throwIfCancelled();
        assertThat(context.jobId()).isEqualTo(id);
        assertThatThrownBy(() -> new JobContext(id, 0, () -> false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JobContext(null, 1, () -> false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("context");
        assertThatThrownBy(() -> new JobContext(id, 1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("context");
        assertThatThrownBy(new JobContext(id, 1, () -> true)::throwIfCancelled)
                .isInstanceOf(java.util.concurrent.CancellationException.class);

        JobResult<String> success = new JobResult.Success<>(id, "value", Duration.ofMillis(1), 1);
        JobResult<String> failure = new JobResult.Failure<>(
                id,
                JobResult.FailureInfo.from(new IllegalStateException("failure")),
                Duration.ZERO,
                1,
                false);
        JobResult<String> cancelled = new JobResult.Cancelled<>(id, "cancelled", Duration.ZERO);
        assertThat(success.value()).contains("value");
        assertThat(failure.value()).isEmpty();
        assertThat(cancelled.value()).isEmpty();
        assertThat(success.isSuccess()).isTrue();
    }

    @Test
    void workflowProducesPriorityAwareLayers() {
        var fetch = JobDefinition.of(new JobId("fetch"), ignored -> 1);
        var audit = new JobDefinition<>(
                new JobId("audit"), Set.of(), 10, Duration.ofSeconds(1), RetryPolicy.none(), ignored -> 2);
        var index = new JobDefinition<>(
                new JobId("index"),
                Set.of(fetch.id(), audit.id()),
                0,
                Duration.ofSeconds(1),
                RetryPolicy.none(),
                ignored -> 3);
        var workflow = new WorkflowDefinition(List.of(fetch, audit, index));
        assertThat(workflow.plan().ordered()).containsExactly(audit.id(), fetch.id(), index.id());
        assertThat(workflow.plan().layers()).containsExactly(List.of(audit.id(), fetch.id()), List.of(index.id()));
        assertThat(workflow.jobs()).hasSize(3);
        assertThatThrownBy(() -> workflow.jobs().put(new JobId("x"), fetch))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void workflowRejectsDuplicatesUnknownDependenciesAndCycles() {
        var a = JobDefinition.of(new JobId("a"), ignored -> null);
        assertThatThrownBy(() -> new WorkflowDefinition(List.of(a, a)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
        var unknown = new JobDefinition<>(
                new JobId("unknown"),
                Set.of(new JobId("missing")),
                0,
                Duration.ofSeconds(1),
                RetryPolicy.none(),
                ignored -> null);
        assertThatThrownBy(() -> new WorkflowDefinition(List.of(unknown)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
        var first = new JobDefinition<>(
                new JobId("first"), Set.of(new JobId("second")), 0, Duration.ofSeconds(1), RetryPolicy.none(),
                ignored -> null);
        var second = new JobDefinition<>(
                new JobId("second"), Set.of(new JobId("first")), 0, Duration.ofSeconds(1), RetryPolicy.none(),
                ignored -> null);
        assertThatThrownBy(() -> new WorkflowDefinition(List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }
}
