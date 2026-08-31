package io.github.naokiko.orchestrator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/** Validated immutable DAG and deterministic execution plan. */
public final class WorkflowDefinition {
    private final Map<JobId, JobDefinition<?>> jobs;
    private final ExecutionPlan plan;

    public WorkflowDefinition(Collection<? extends JobDefinition<?>> definitions) {
        Objects.requireNonNull(definitions, "definitions");
        var mutable = new LinkedHashMap<JobId, JobDefinition<?>>();
        for (var definition : definitions) {
            if (mutable.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalArgumentException("duplicate job id: " + definition.id());
            }
        }
        jobs = Map.copyOf(mutable);
        plan = buildPlan();
    }

    public Map<JobId, JobDefinition<?>> jobs() {
        return jobs;
    }

    public ExecutionPlan plan() {
        return plan;
    }

    private ExecutionPlan buildPlan() {
        var unknown = jobs.values().stream()
                .flatMap(job -> job.dependencies().stream())
                .filter(dependency -> !jobs.containsKey(dependency))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("unknown dependencies: " + unknown);
        }

        var indegree = new HashMap<JobId, Integer>();
        var dependents = new HashMap<JobId, Set<JobId>>();
        jobs.keySet().forEach(id -> {
            indegree.put(id, jobs.get(id).dependencies().size());
            dependents.put(id, new HashSet<>());
        });
        jobs.values().forEach(job -> job.dependencies()
                .forEach(dependency -> dependents.get(dependency).add(job.id())));

        Comparator<JobId> priority = Comparator
                .<JobId>comparingInt(id -> jobs.get(id).priority())
                .reversed()
                .thenComparing(JobId::value);
        var ready = new PriorityQueue<>(priority);
        indegree.forEach((id, degree) -> {
            if (degree == 0) {
                ready.add(id);
            }
        });

        var ordered = new ArrayList<JobId>();
        var depth = new HashMap<JobId, Integer>();
        while (!ready.isEmpty()) {
            var id = ready.remove();
            ordered.add(id);
            var jobDepth = jobs.get(id).dependencies().stream().mapToInt(depth::get).max().orElse(-1) + 1;
            depth.put(id, jobDepth);
            dependents.get(id).forEach(dependent -> {
                var remaining = indegree.compute(dependent, (ignored, current) -> current - 1);
                if (remaining == 0) {
                    ready.add(dependent);
                }
            });
        }
        if (ordered.size() != jobs.size()) {
            var cyclic = jobs.keySet().stream().filter(id -> !ordered.contains(id)).sorted().toList();
            throw new IllegalArgumentException("dependency cycle contains: " + cyclic);
        }

        var layers = new ArrayList<List<JobId>>();
        for (var id : ordered) {
            while (layers.size() <= depth.get(id)) {
                layers.add(new ArrayList<>());
            }
            layers.get(depth.get(id)).add(id);
        }
        return new ExecutionPlan(
                List.copyOf(ordered), layers.stream().map(List::copyOf).toList());
    }

    public record ExecutionPlan(List<JobId> ordered, List<List<JobId>> layers) {
        public ExecutionPlan {
            ordered = List.copyOf(ordered);
            layers = layers.stream().map(List::copyOf).toList();
        }

        @Override
        public List<List<JobId>> layers() {
            return layers.stream().map(List::copyOf).toList();
        }
    }
}
