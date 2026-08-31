package io.github.naokiko.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowBuilderTest {
    @Test
    void buildsSimpleGenericWorkflowWithDomainDefaults() throws Exception {
        JobBuilder<String> job = WorkflowBuilder.create().job("fetch", context -> "payload");
        var workflow = job.done().build();

        var definition = workflow.jobs().get(new JobId("fetch"));
        assertThat(definition.dependencies()).isEmpty();
        assertThat(definition.priority()).isZero();
        assertThat(definition.timeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.none());
        assertThat(definition.action().execute(new JobContext(
                definition.id(), 1, () -> false))).isEqualTo("payload");
    }

    @Test
    void buildsBranchingDagWithDeterministicDependencyOrder() {
        var workflow = WorkflowBuilder.create()
                .job("fetch-users", context -> List.of("Ada")).done()
                .job("fetch-orders", context -> List.of(7)).done()
                .job("aggregate", context -> "ready")
                    .dependsOn("fetch-users", "fetch-orders")
                    .done()
                .job("publish", context -> "published")
                    .dependsOn(new JobId("aggregate"))
                    .done()
                .build();

        assertThat(workflow.plan().layers()).containsExactly(
                List.of(new JobId("fetch-orders"), new JobId("fetch-users")),
                List.of(new JobId("aggregate")),
                List.of(new JobId("publish")));
    }

    @Test
    void propagatesPriorityTimeoutAndRetryConfiguration() {
        var retry = new RetryPolicy(
                3, Duration.ofMillis(10), Duration.ofSeconds(1), 2, 0.2);
        var workflow = WorkflowBuilder.create()
                .job(new JobId("configured"), context -> 42)
                    .priority(17)
                    .timeout(Duration.ofSeconds(3))
                    .retry(retry)
                    .done()
                .build();

        var definition = workflow.jobs().get(new JobId("configured"));
        assertThat(definition.priority()).isEqualTo(17);
        assertThat(definition.timeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(definition.retryPolicy()).isSameAs(retry);
    }

    @Test
    void rejectsDuplicateIdsAndSelfDependencies() {
        var duplicate = WorkflowBuilder.create()
                .job("same", context -> 1).done();
        assertThatThrownBy(() -> duplicate.job("same", context -> 2).done())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate job id");

        assertThatThrownBy(() -> WorkflowBuilder.create()
                .job("self", context -> 1)
                .dependsOn("self")
                .done())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot depend on itself");
    }

    @Test
    void delegatesTimeoutAndGraphValidationToDomainModels() {
        assertThatThrownBy(() -> WorkflowBuilder.create()
                .job("invalid-timeout", context -> 1)
                .timeout(Duration.ZERO)
                .done())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout must be positive");

        assertThatThrownBy(() -> WorkflowBuilder.create()
                .job("dependent", context -> 1)
                .dependsOn("missing")
                .done()
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown dependencies");
    }

    @Test
    void buildCreatesImmutableSnapshotsAndBuilderRemainsReusable() {
        var builder = WorkflowBuilder.create()
                .job("first", context -> 1).done();
        var firstSnapshot = builder.build();
        var secondSnapshot = builder
                .job("second", context -> 2).done()
                .build();

        assertThat(firstSnapshot.jobs()).containsOnlyKeys(new JobId("first"));
        assertThat(secondSnapshot.jobs()).containsOnlyKeys(new JobId("first"), new JobId("second"));
        assertThatThrownBy(() -> firstSnapshot.jobs().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void jobStepIsSingleUseAndRejectsNullConfiguration() {
        var step = WorkflowBuilder.create().job("once", context -> 1);
        step.done();
        assertThatThrownBy(step::done).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> step.priority(1)).isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> WorkflowBuilder.create().job((JobId) null, context -> 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WorkflowBuilder.create().job("null-action", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WorkflowBuilder.create().job("null-timeout", context -> 1).timeout(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WorkflowBuilder.create().job("null-retry", context -> 1).retry(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void dependencyMethodsRejectNullInputs() {
        var step = WorkflowBuilder.create().job("job", context -> 1);
        assertThatThrownBy(() -> step.dependsOn((String[]) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> step.dependsOn((JobId[]) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> step.dependsOn(new JobId[] {null}))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> step.dependsOn(new String[] {null}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
