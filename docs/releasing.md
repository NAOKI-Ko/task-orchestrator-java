# Releasing

The project follows semantic versioning and publishes build outputs through an annotated tag on
`main`. Maven Central is optional until signing and publishing credentials are explicitly configured.

## Prepare and review

1. Start `release/vX.Y.Z` from the latest `main`.
2. Update `version` in `build.gradle.kts` and move completed changelog entries into a dated Keep a
   Changelog section.
3. On Java 25, run:

   ```bash
   ./gradlew clean check spotbugsMain spotbugsTest jmhClasses --no-daemon
   ./gradlew jar sourcesJar javadocJar --no-daemon
   ```

4. Inspect the main, sources, and Javadoc JARs under `build/libs/`.
5. Open a pull request that records quality gates, packaging, release behavior, and breaking changes.
6. Merge only after CI succeeds, then fast-forward the local `main` checkout.

## Tag and publish

Create and push an annotated `vX.Y.Z` tag at the reviewed `main` commit. The release workflow checks
that the tag matches the Gradle project version, reruns Checkstyle, SpotBugs, tests, JaCoCo line and
branch gates, and JMH compilation, then attaches all three JARs to a non-draft GitHub Release.

Publishing to a package registry requires deliberately configured signing and repository secrets.
Never commit signing keys or tokens, and do not claim Maven Central availability until publication
has been independently verified.

## Failed release or rollback

Never move or force-push a published tag. If the workflow fails before a Release exists, fix the
cause through a new pull request and determine whether the unused tag may be removed or the next
patch version should be used. Once artifacts are public, keep them auditable, document any defect,
and publish a corrected patch version instead of silently replacing binaries.
