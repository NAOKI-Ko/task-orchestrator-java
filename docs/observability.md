# OpenTelemetry observability

The optional adapter translates the engine's existing `WorkflowEvent` stream and `Metrics` calls
into traces and metrics. The orchestration engine contains no SDK lifecycle, exporter, batching, or
transport code. Applications keep ownership of those operational choices.

The build uses the official OpenTelemetry Java API 1.65.0, the stable release published on
2026-08-07. The OpenTelemetry SDK and in-memory exporters are test dependencies, not core runtime
dependencies. See the [official release](https://github.com/open-telemetry/opentelemetry-java/releases/tag/v1.65.0).

## Setup

Pass an application-configured `OpenTelemetry` instance to the factory, then connect the returned
ports to one engine:

```java
OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();

try (var telemetry = OrchestratorTelemetry.create(openTelemetry);
        var engine = new WorkflowEngine(8, telemetry.eventSink(), telemetry.metrics())) {
    WorkflowResult result = engine.execute(workflow).join();
}
```

`OrchestratorTelemetry` does not close or flush the supplied SDK. Configure the SDK, resource,
sampler, processors, readers, exporters, credentials, batching, and shutdown in the hosting
application. Use one telemetry pair per concurrently active engine. One pair can observe sequential
workflows because the event protocol intentionally has no global workflow identifier.

## Trace hierarchy

Each execution produces one root and one child per terminal job, including jobs skipped because a
dependency failed:

```text
workflow.execute
  ├── workflow.job  task.orchestrator.job.id=fetch
  ├── workflow.job  task.orchestrator.job.id=transform
  └── workflow.job  task.orchestrator.job.id=persist
```

The adapter explicitly assigns the workflow span as every job span's parent. It does not rely on
thread-local context, so jobs running on different virtual threads retain the correct relationship.
Successful spans use `OK`; failed and cancelled job spans and unsuccessful workflow spans use
`ERROR`.

## Attributes

These custom keys form the stable library vocabulary. The `task.orchestrator` prefix avoids claiming
attributes from OpenTelemetry semantic conventions where no workflow-orchestrator convention exists.

| Key | Type | Meaning |
| --- | --- | --- |
| `task.orchestrator.workflow.job_count` | long | Jobs in the workflow. |
| `task.orchestrator.workflow.result` | string | `success`, `failure`, or adapter-side `incomplete`. |
| `task.orchestrator.job.id` | string | Workflow-local job ID. |
| `task.orchestrator.job.attempt` | long | Latest one-based attempt. |
| `task.orchestrator.job.state` | string | Latest lifecycle state. |
| `task.orchestrator.job.result` | string | `success`, `failure`, `cancelled`, or `incomplete`. |
| `task.orchestrator.job.dependency_failure` | boolean | Whether an unsuccessful dependency skipped the job. |
| `task.orchestrator.job.dependency.id` | string | Unsuccessful dependency ID, when applicable. |
| `task.orchestrator.retry.delay` | double | Retry delay in seconds. |
| `task.orchestrator.job.timeout` | double | Configured attempt timeout in seconds. |
| `task.orchestrator.metric.name` | string | Original `Metrics` port measurement name. |

Failure span events use the standard `exception` event name and `exception.type` attribute. The
adapter deliberately omits `exception.message` and `exception.stacktrace`; see the privacy policy
below.

## Span events

| Event | Meaning |
| --- | --- |
| `job.attempt` | An attempt started; includes its number. |
| `job.retry` | Another attempt was scheduled; includes next attempt and delay. |
| `job.timeout` | An attempt exceeded its configured timeout. |
| `job.dependency_failure` | The job was skipped because a named dependency was unsuccessful. |
| `exception` | A job failed; contains exception type but not message or stack. |
| `job.cancelled` | Cooperative or interruption-driven cancellation ended the job. |

## Metrics

The adapter preserves the existing metrics-port calls without measuring the engine a second time.

| Instrument | Kind | Unit | Values |
| --- | --- | --- | --- |
| `task.orchestrator.job.events` | monotonic long counter | `{event}` | One for each `job.completed`, `job.retried`, or `job.failed` call. |
| `task.orchestrator.job.duration` | double histogram | `s` | Existing nanosecond durations converted to seconds. |

Both instruments include job ID and original measurement name attributes. Duration uses seconds,
the OpenTelemetry/UCUM convention for time instruments, rather than exporting the engine's internal
nanosecond value with ambiguous scale. Cancellation currently has no metrics-port call, so its state
is represented in tracing rather than inventing a duplicate counter.

## Privacy and data policy

The adapter automatically exports configured job IDs, lifecycle state, attempt counts, timing,
dependency IDs, terminal results, and exception class names. It never exports job arguments, return
values, action payloads, exception messages, or stack traces. Treat job IDs as telemetry metadata and
avoid embedding tenant data, credentials, or personal information in them.

Exporter-side resource attributes, baggage, redaction, sampling, retention, access control, and data
residency remain application responsibilities. The adapter does not install an exporter and makes no
network calls by itself.

## Example

`OpenTelemetryExample` uses the no-op API so it runs without an SDK or exporter dependency; replace
that API with the application's configured SDK:

```bash
./gradlew runOpenTelemetryExample
```

Integration tests use the real SDK with in-memory span and metric exporters to verify hierarchy,
status, events, units, concurrent jobs, and payload non-disclosure.
