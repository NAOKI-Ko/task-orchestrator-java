package io.github.naokiko.orchestrator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2)
@Measurement(iterations = 3)
public class WorkflowPlanBenchmark {
    @Param({"100", "1000"})
    private int nodes;

    private List<JobDefinition<?>> definitions;

    @Setup(Level.Trial)
    public void setUp() {
        definitions = new ArrayList<>(nodes);
        for (int index = 0; index < nodes; index++) {
            var result = index;
            var id = new JobId("job-" + index);
            var dependencies = index == 0 ? Set.<JobId>of() : Set.of(new JobId("job-" + ((index - 1) / 2)));
            definitions.add(new JobDefinition<>(
                    id,
                    dependencies,
                    index % 10,
                    java.time.Duration.ofSeconds(1),
                    RetryPolicy.none(),
                    ignored -> result));
        }
    }

    @Benchmark
    public WorkflowDefinition planDag() {
        return new WorkflowDefinition(definitions);
    }
}
