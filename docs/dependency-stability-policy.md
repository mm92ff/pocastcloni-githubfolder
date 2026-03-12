# Dependency Stability & Version Policy

## 1. Focus Area Goal
Establish and document a stable-only dependency posture for the `app` module, now backed by the `gradle/libs.versions.toml` catalog so every explicit artifact (Compose BOM 2024.02.02, Room 2.6.1, Paging 3.2.1, Media3 1.3.0, Retrofit 2.9.0, OkHttp 4.12.0, Datastore 1.0.0, WorkManager 2.9.0, Jackson 2.15.2, etc.) is pinned as a production release, and describe how ongoing monitoring and fallbacks keep Kotlin Android Studio production-ready.

## 2. Best Practices
- Lock each dependency to a specific semantically versioned release and document the reasoning for BOM/group alignment.
- Prefer platform BOMs (Compose BOM 2024.02.02) and package-managed bundles so higher-level modules inherit a consistent stable set instead of ad-hoc pre-release coordinates.
- Capture the last known stable version of risky families (Paging, Media3) in text or a `gradle.properties` flag so automated reviews can compare current versus recorded values.
- For any feature that truly needs a pre-release artifact, introduce a stable fallback path (code guard or feature flag) and clearly communicate the risk, rather than allowing unstable APIs to leak into common configuration.

## 3. Common Mistakes
- Blindly updating to the latest tag from release notes, which may be an alpha/beta/RC without verifying flags in the Gradle catalog or BOM.
- Mixing stable and pre-release artifacts inside the same dependency family without isolation, causing runtime crashes that only reproduce after compose recompilation.
- Forgetting to pin `ksp` or Hilt compiler plugins to the same versions in the Gradle cache, which can roll forward to incompatible betas when Gradle refreshes.

## 4. Audit Criteria
- Every dependency in `app/build.gradle.kts` must be flagged as `Release` (no alpha/beta/rc) before merging, with the latest BOM and the `gradle/libs.versions.toml` catalog documented for quick review.
- Required checks at merge time: `./gradlew test` + `./gradlew assemble` to ensure pre-release drift would fail fast.
- Optional but recommended: `./gradlew lint`, `./gradlew ktlintCheck`, `./gradlew detekt`, `./gradlew spotlessCheck` to keep style changes from masking dependency updates.
- Linchpin check missing today: a dependency-update gate such as `./gradlew dependencyUpdates` (via the Ben Manes Versions plugin) or `./gradlew dependencyInsight` should run before release so a pre-release coordinate in the catalog fails validation before code review.
- Audit trailing `ksp` and `kotlinCompilerExtensionVersion` consistency so plugin updates cannot cross incompatible versions.

## 5. Concrete Refactorings / Improvements
- Create and maintain `gradle/libs.versions.toml` so the stable versions of `androidx.media3`, `androidx.paging`, `androidx.room`, and the rest of the platform are centrally declared and can be scanned for any `alpha`/`beta`/`rc` substrings before merging.
- Document the stable fallback pattern in code (see example) so engineers unlocking features know how to continue shipping if the pre-release channel regresses.
- Update CI docs or README to call out the dependency stability policy, so future contributors know to re-run the required checks before releasing.

## 6. Small Kotlin Example
```kotlin
val securePlayerFactory: Lazy<MediaPlayerFactory> = lazy {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        StableAudioPlayerFactory()
    } else {
        @Suppress("DEPRECATION")
        LegacyAudioPlayerFactory()
    }
}

// If a new Media3 preview artifact is ever enabled, wrap the preview API behind a feature flag
// while continuing to compile against the stable player factory for the default path.
```
This snippet shows guarding unstable APIs so the app can fallback to the last supported runtime class, keeping production builds stable even if a preview library sneaks in.

## 7. Priority
1. Verify that every dependency remains a stable release and capture the baseline list in documentation (done for current versions).
2. Establish tooling/automation to detect when pre-release coordinates are added (e.g., Gradle update scripts, turnover checks).
3. Keep required dependency checks (`test`, `assemble`) easily runnable from `README` or CI to prevent latent drift.

## 8. Dependencies To Other Areas
- Testing Infrastructure: required checks (`./gradlew test`, `./gradlew assemble`) are owned by the testing focus area; coordination ensures the dependency policy has a gate.
- Release/Signing Process: dependency stability directly impacts release validation, so release automation must include the documented checks before signing artifacts.

## 9. Irritations / Failure Injection and Expected Behavior
1. **Build failure when a pre-release artifact sneaks into `gradle/libs.versions.toml` during a BOM refresh.** Expected behavior: the catalog scan (or an automated `./gradlew dependencyUpdates` run) rejects the PR, the pipeline halts before `assemble`, and reviewers follow the documented fallback path instead of shipping the preview API.
2. **Unexpected runtime crash because a beta API changed behavior after a transitive update.** Expected behavior: monitoring and staging catch the regression, the team rolls back to the prior stable catalog version, and a post-mortem records why the pre-release dependency passed the review gate.
3. **Manual override required when Gradle refreshes to a new preview version of `ksp` or `hilt-android-compiler`.** Expected behavior: plugin versions stay pinned (e.g., they are declared in the version catalog), release docs remind maintainers to bump them together, and any override is accompanied by a regression test and policy exception note.

### Required Checks (not run during evaluation/implementation)
- `./gradlew test`
- `./gradlew assemble`
- Optional check candidates for future confidence: `./gradlew lint`, `./gradlew ktlintCheck`, `./gradlew detekt`, `./gradlew spotlessCheck`
