package io.github.naokiko.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkflowUtilitiesTest {
    private static final JobId JOB = new JobId("job");
    private static final WorkflowEvent STARTED =
            new WorkflowEvent.JobStarted(JOB, 1, Instant.EPOCH);
    private static final WorkflowEvent COMPLETED =
            new WorkflowEvent.JobCompleted(JOB, 1, Duration.ZERO, Instant.EPOCH);

    @Test
    void compositeSinkPreservesOrderAndCopiesInput() {
        var invocations = new ArrayList<String>();
        var delegates = new ArrayList<EventSink>();
        delegates.add(event -> invocations.add("first"));
        delegates.add(event -> invocations.add("second"));
        var sink = new CompositeEventSink(delegates);
        delegates.clear();

        sink.publish(STARTED);

        assertThat(invocations).containsExactly("first", "second");
    }

    @Test
    void compositeSinkIsEmptySafeNullRejectingAndFailFast() {
        EventSinks.fanOut().publish(STARTED);
        assertThatThrownBy(() -> new CompositeEventSink(null))
                .isInstanceOf(NullPointerException.class);
        var withNull = new ArrayList<EventSink>();
        withNull.add(EventSink.noop());
        withNull.add(null);
        assertThatThrownBy(() -> new CompositeEventSink(withNull))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> EventSinks.fanOut((EventSink[]) null))
                .isInstanceOf(NullPointerException.class);

        var invocations = new ArrayList<String>();
        var sink = EventSinks.fanOut(
                event -> invocations.add("first"),
                event -> { throw new IllegalStateException("sink failed"); },
                event -> invocations.add("last"));
        assertThatThrownBy(() -> sink.publish(STARTED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("sink failed");
        assertThat(invocations).containsExactly("first");
        assertThatThrownBy(() -> sink.publish(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void filteringSinkForwardsOnlyMatchesAndRejectsNulls() {
        var received = new ArrayList<WorkflowEvent>();
        var sink = EventSinks.filter(received::add, WorkflowEvent.JobCompleted.class::isInstance);

        sink.publish(STARTED);
        sink.publish(COMPLETED);

        assertThat(received).containsExactly(COMPLETED);
        assertThatThrownBy(() -> new FilteringEventSink(null, event -> true))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FilteringEventSink(EventSink.noop(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> sink.publish(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void retryFactoriesCreatePoliciesWithExpectedBoundaries() {
        assertThat(RetryPolicies.none()).isEqualTo(RetryPolicy.none());

        var fixed = RetryPolicies.fixed(3, Duration.ofMillis(25));
        assertThat(fixed.delayFor(1, 0)).isEqualTo(Duration.ofMillis(25));
        assertThat(fixed.delayFor(2, 1)).isEqualTo(Duration.ofMillis(25));

        var exponential = RetryPolicies.exponential(
                4, Duration.ofMillis(10), Duration.ofMillis(50));
        assertThat(exponential.delayFor(1, 0.5)).isEqualTo(Duration.ofMillis(10));
        assertThat(exponential.delayFor(2, 0.5)).isEqualTo(Duration.ofMillis(20));
        assertThat(exponential.delayFor(4, 0.5)).isEqualTo(Duration.ofMillis(50));

        var jittered = RetryPolicies.exponentialWithJitter(
                3, Duration.ofMillis(100), Duration.ofSeconds(1), 0.25);
        assertThat(jittered.delayFor(1, 0)).isEqualTo(Duration.ofMillis(75));
        assertThat(jittered.delayFor(1, 1)).isEqualTo(Duration.ofMillis(125));
    }

    @Test
    void retryFactoriesDelegateInvalidBoundariesToRetryPolicy() {
        assertThatThrownBy(() -> RetryPolicies.fixed(0, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RetryPolicies.fixed(2, Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RetryPolicies.exponential(
                2, Duration.ofSeconds(2), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RetryPolicies.exponentialWithJitter(
                2, Duration.ZERO, Duration.ZERO, 1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void executionSummaryCountsEveryResultVariant() {
        var results = new LinkedHashMap<JobId, JobResult<?>>();
        results.put(new JobId("success"), new JobResult.Success<>(
                new JobId("success"), "value", Duration.ofMillis(1), 1));
        results.put(new JobId("failure"), new JobResult.Failure<>(
                new JobId("failure"), new JobResult.FailureInfo("IOException", "reset"),
                Duration.ofMillis(2), 2, false));
        results.put(new JobId("dependency"), new JobResult.Failure<>(
                new JobId("dependency"), new JobResult.FailureInfo("IllegalStateException", "dependency"),
                Duration.ZERO, 0, true));
        results.put(new JobId("cancelled"), new JobResult.Cancelled<>(
                new JobId("cancelled"), "requested", Duration.ofMillis(3)));

        var summary = ExecutionSummary.from(new WorkflowResult(results, Duration.ofMillis(9)));

        assertThat(summary).isEqualTo(new ExecutionSummary(4, 1, 2, 1, 1, Duration.ofMillis(9)));
    }

    @Test
    void executionSummaryRejectsNullAndInconsistentValues() {
        assertThatThrownBy(() -> ExecutionSummary.from(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ExecutionSummary(-1, 0, 0, 0, 0, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExecutionSummary(2, 1, 0, 0, 0, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExecutionSummary(1, 0, 1, 0, 2, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExecutionSummary(0, 0, 0, 0, 0, Duration.ofNanos(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExecutionSummary(0, 0, 0, 0, 0, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void validatorReusesCanonicalWorkflowValidationWithoutThrowing() {
        var first = JobDefinition.of(new JobId("first"), context -> 1);
        var second = new JobDefinition<>(
                new JobId("second"), Set.of(first.id()), 0, Duration.ofSeconds(1),
                RetryPolicy.none(), context -> 2);
        var valid = WorkflowValidator.validate(List.of(first, second));
        assertThat(valid.valid()).isTrue();
        assertThat(valid.errors()).isEmpty();
        valid.throwIfInvalid();

        var unknown = new JobDefinition<>(
                new JobId("unknown"), Set.of(new JobId("missing")), 0, Duration.ofSeconds(1),
                RetryPolicy.none(), context -> 1);
        var invalid = WorkflowValidator.validate(List.of(unknown));
        assertThat(invalid.valid()).isFalse();
        assertThat(invalid.errors()).singleElement().asString().contains("unknown dependencies");
        assertThatThrownBy(invalid::throwIfInvalid)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown dependencies");
        assertThatThrownBy(() -> invalid.errors().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void validationResultAndValidatorRejectInvalidInputsConsistently() {
        var nullDefinitions = WorkflowValidator.validate(null);
        assertThat(nullDefinitions.valid()).isFalse();
        assertThat(nullDefinitions.errors()).isNotEmpty();
        var nullElement = WorkflowValidator.validate(java.util.Arrays.asList((JobDefinition<?>) null));
        assertThat(nullElement.valid()).isFalse();
        assertThat(nullElement.errors()).containsExactly("definitions must not contain null");

        assertThatThrownBy(() -> new WorkflowValidationResult(true, List.of("error")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkflowValidationResult(false, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkflowValidationResult(true, null))
                .isInstanceOf(NullPointerException.class);
    }
}
