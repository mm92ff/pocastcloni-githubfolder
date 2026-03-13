# Release Hardening & Build Safety

## 1. Focus Area Goal
Document the current release build hardening, identify gaps in obfuscation/minification safety, signing, startup resiliency, and rollout controls so the team can prioritize concrete hardening work before the next production submission.

## 2. Current Hardening Status
- `app/build.gradle.kts:32` enables `isMinifyEnabled`, `isShrinkResources`, and applies both the Android optimize ProGuard template and `app/proguard-rules.pro`. Compose, JVM 17, and Kotlin compiler options are already release-ready, but no further release-specific build-time guards exist (no `debuggable = false` override, no signing block, no per-flavor dimension for rollout control).
- `app/proguard-rules.pro` captures reflection-preservation rules for the app package, Retrofit/request interfaces, TikXML, and Room converters, which covers the core dependencies currently in use. The file therefore lowers the risk of missing types getting stripped, but it does not document mapping upload, safety assertions, or per-library keep rules that might be needed when new reflection-heavy modules arrive.

## 3. Best Practices
1. **Enable deterministic release signing** (not just relying on the default debug keystore) with a `signingConfigs` block and `release.signingConfig` in Gradle. Document rotation and secure storage of keystore credentials.
2. **Transmit mapping files** for every release to the crash backend (Play console, Firebase/Crashlytics) and verify mapping uploads through Gradle tasks so symbolic breakdown works after obfuscation.
3. **Explicitly disable debuggable releases** and guard runtime flags (e.g., `android:debuggable="false"` in manifests or `buildTypes.release.isDebuggable = false`).
4. **Startup stability hooks**: gate crash-reporting toggles and feature flags behind `BuildConfig` checks, initialize critical services conditionally, and fail fast with clear logs for missing resources.
5. **Rollout risk controls**: Stage releases via `versionCode` gating, integrate with Firebase Remote Config or Play feature stages, and include `Crashlytics` or custom boot-time health checks before enabling key flows.

## 4. Common Mistakes (Observed in Repository)
- **Missing release signing config**: There is no `signingConfigs` block, so Gradle defaults to the debug keystore; Play Store upload will fail or get rejected and there is no key rotation plan documented around `app/build.gradle.kts`.
- **No mapping / symbol handling**: `app/proguard-rules.pro` lacks comments or automation for uploading mapping files, making in-market debugging impossible after obfuscation.
- **No startup contract**: Crash reporting and rollout instrumentation (e.g., `Crashlytics` initialization gated by a surge detection flag) are absent; regressions will remain invisible until user reports arrive for `release` builds.

## 5. Audit Criteria
| Check Type | Description | Status | Missing/Upgrade Candidate |
| --- | --- | --- | --- |
| Required | `./gradlew assembleRelease` with explicit `signingConfig` ensures release artifacts are signed with production key | Not present | Signing config is missing entirely. |
| Required | Upload mapping file to Play/Crashlytics after obfuscation | Not automated | No Gradle task or docs. |
| Required | Release build debuggability gate (`isDebuggable = false`) | Implicit default | Should be explicit to avoid mistakes. |
| Optional | `BuildConfig` flag disabling analytics/crash reporting until rollouts stabilize | Not present | Add a release guard for Crashlytics/A/B toggles. |
| Optional | Startup monotonic health check emitting metrics on boot (`ProcessLifecycleOwner`) | Not present | Consider for early detection.

## 6. Concrete Refactorings / Improvements (Documentation Only)
1. Add a `signingConfigs` block in `app/build.gradle.kts` and wire it into the `release` build type, referencing securely stored keystore paths/credentials (e.g., `keystoreProperties`).
2. Document a `publishRelease` checklist that includes: running `./gradlew :app:bundleRelease`, uploading mapping to Play/Crashlytics, verifying `versionCode` increments, and enabling staged rollout.
3. Introduce a Gradle task or GitHub Action step that uploads the mapping file produced by R8/ProGuard (e.g., uploadCrashlyticsMappingFile).
4. Create a Kotlin guard (see section 7) that only initializes crash-reporting + feature toggles in a release-safe manner (tied to `BuildConfig.DEBUG`).
5. Log startup health (cold start, key service init) in `MainActivity` or a new `StartupMonitor` class and tie these logs to telemetry for release monitoring.

## 7. Small Kotlin Example
```kotlin
fun safeCrashlyticsInit(context: Context) {
    if (!BuildConfig.DEBUG && BuildConfig.FLAVOR == "release") {
        Crashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        StartupMonitor.recordBootTime(context)
    } else {
        Crashlytics.getInstance().setCrashlyticsCollectionEnabled(false)
    }
}
```
This snippet should live in a release-only guard (triggered from the application class) so instrumentation does not leak into debug builds and startup timing is observable.

## 8. Priority
1. Release signing and production keystore setup (hard blocker for Play upload).
2. Mapping file handling and crash reporting instrumentation (critical for debugging post-ship regressions).
3. Startup health metrics + staged rollout hooks (high priority for safe rollouts).

## 9. Dependencies To Other Areas
- **Testing & Release QA**: Need automated smoke tests to verify release flows after signing and ProGuard adjustments.
- **Crash Monitoring / Observability**: Depends on Crashlytics/Firebase instrumentation or an equivalent telemetry backend for rollout risk control.
- **CI/CD Automation**: Reuses the same artifacts for instrumentation and ensures bundling + mapping upload run before publishing.

## 10. Irritations / Failure Injection and Expected Behavior
1. **Irritation**: Obfuscation strips Retrofit annotations and runtime reflection data, causing parsing failures during early release testing. **Expected behavior**: Existing ProGuard rules should preserve Retrofit/TikXML types, verified by running `./gradlew :app:assembleRelease` and executing a Retrofit request against staging; logs should show no `NoSuchMethodException` or `JsonMappingException` and offline unit tests should fail fast if a rule is missing.
2. **Failure Injection**: Release build still signed with debug keystore; Play Console declines upload or Play Protect crashes on install. **Expected behavior**: Gradle should fail with `SigningConfig not found` or `zipalign` warning when new release keystore is absent, and release checklist should halt until `signingConfigs` points to production keystore stored securely.
3. **Irritation**: Startup logs show Crashlytics initialized in debug; release testers see duplicate reports or false positives. **Expected behavior**: Guard the initialization with `BuildConfig.DEBUG` and rollout flag so only release builds log crashes, and a small chromium-style kill switch can turn off instrumentation if a rollout spike occurs.

## 11. Next Steps
1. Document a release checklist (keystore, mapping, staged rollout) in `docs/`.
2. Share the audit criteria and missing checks in team sync so the CI pipeline can cover the gaps.
