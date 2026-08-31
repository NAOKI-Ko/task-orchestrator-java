# Contributing

Contributions that make orchestration safer, clearer or more observable are welcome.

1. Discuss significant API or execution-semantics changes in an issue.
2. Branch from `main` and keep commits focused on meaningful behavior.
3. Add tests for the success path, failure propagation, timeout and cancellation where relevant.
4. Run `./gradlew check spotbugsMain spotbugsTest` on Java 25.
5. Update architecture and usage documentation for visible behavior changes.
6. Open a pull request with the problem, design, trade-offs and verification evidence.

Do not add sleeps as synchronization in concurrency tests when a latch or barrier can express the
condition. Benchmark results must include the environment and must not be represented as universal.
Keep public models immutable and new event variants exhaustive.

By participating, you agree to collaborate respectfully and keep review focused on the work.

