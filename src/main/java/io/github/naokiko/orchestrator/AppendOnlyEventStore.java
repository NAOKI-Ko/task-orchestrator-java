package io.github.naokiko.orchestrator;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/** Newline-delimited local event log using stable, dependency-free JSON. */
public final class AppendOnlyEventStore implements EventStore {
    private final Path path;
    private final ReentrantLock lock = new ReentrantLock();

    public AppendOnlyEventStore(Path path) {
        this.path = Objects.requireNonNull(path, "path");
    }

    @Override
    public void publish(WorkflowEvent event) {
        Objects.requireNonNull(event, "event");
        var line = serialize(event) + System.lineSeparator();
        lock.lock();
        try {
            var parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    path,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (java.io.IOException error) {
            throw new UncheckedIOException("could not append workflow event", error);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<String> replay() throws java.io.IOException {
        lock.lock();
        try {
            return Files.exists(path) ? List.copyOf(Files.readAllLines(path, StandardCharsets.UTF_8)) : List.of();
        } finally {
            lock.unlock();
        }
    }

    static String serialize(WorkflowEvent event) {
        var details = switch (event) {
            case WorkflowEvent.JobStarted started -> "\"jobId\":\"" + escape(started.jobId().value())
                    + "\",\"attempt\":" + started.attempt();
            case WorkflowEvent.JobCompleted completed -> "\"jobId\":\"" + escape(completed.jobId().value())
                    + "\",\"attempts\":" + completed.attempts();
            case WorkflowEvent.JobFailed failed -> "\"jobId\":\"" + escape(failed.jobId().value())
                    + "\",\"errorType\":\"" + escape(failed.errorType()) + "\"";
            case WorkflowEvent.JobRetried retried -> "\"jobId\":\"" + escape(retried.jobId().value())
                    + "\",\"nextAttempt\":" + retried.nextAttempt();
            case WorkflowEvent.WorkflowCompleted completed -> "\"jobCount\":" + completed.jobCount();
            case WorkflowEvent.WorkflowFailed failed -> "\"failedJobs\":" + failed.failedJobs();
        };
        return "{\"type\":\"" + event.getClass().getSimpleName() + "\",\"occurredAt\":\""
                + event.occurredAt() + "\"," + details + "}";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}

