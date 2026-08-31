# Changelog

Notable changes follow [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and semantic
versioning.

## [Unreleased]

### Added

- Expanded practical usage, execution semantics, failure handling, concurrency, and operational
  documentation.
- Fluent, typed workflow and job builder APIs with immutable reusable build snapshots.
- Composable event sinks, retry policy factories, execution summaries, and non-throwing workflow
  validation.

## [0.1.0] - 2026-08-31

### Added

- Initial Java 25 virtual-thread DAG orchestration engine.
- Immutable generic jobs, sealed results and explicit lifecycle state machine.
- Dependency resolution, priority planning, cycle detection and failure propagation.
- Retry, timeout, cancellation, concurrency limits, circuit breaker and graceful shutdown.
- Sealed workflow events, append-only persistence, metrics and structured timing logs.
- JUnit 5, AssertJ, jqwik, JaCoCo, Checkstyle, SpotBugs and JMH quality gates.

[Unreleased]: https://github.com/NAOKI-Ko/task-orchestrator-java/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/NAOKI-Ko/task-orchestrator-java/releases/tag/v0.1.0
