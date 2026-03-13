# Evaluation
## Summary
- Release hardening in `app/build.gradle.kts` enables minification and resource shrinking with `proguard-android-optimize.txt` plus a custom `proguard-rules.pro`, which covers key networking, Room, coroutine, and XML parsing rules but lacks mapping upload, stricter disallow of pre-compiled debug libs, and runtime safety controls (e.g., `debuggable false`, `androidTest` vs `release` signing).
- Signing/setup is missing a `signingConfigs` block, so the release build will still use the debug keystore; that’s a hard blocker for production readiness and rollout control (no per-channel keystore, no versioned storage, no key rotation notes).
- Startup stability & rollout risk controls (crash reporting toggles, feature flags, staged rollout hooks) are absent; there’s no monitored `Crashlytics`/`Play Feature Delivery` instrumentation or documented release checklist, so we need to document expected behavior for critical failure scenarios and remediation steps.

## Plan
1. Enumerate release-hardening components already in place (minification, ProGuard rules) and identify gaps in obfuscation safety, mapping preservation, and resource shrink validation.
2. Assess signing setup, startup stability checks, and rollout controls by inspecting Gradle config, manifests, and release artifacts to confirm their absence or incompleteness.
3. Define irritation/failure scenarios tied to this focus area (e.g., obfuscation breaking reflection-backed networking, unsigned release on Play store) with expected remediation behavior, documenting dependencies or missing checks.

## Acceptance Checklist
- [ ] Document current release build hardening (minify/shrink, ProGuard rules, Compose settings) and rate sufficiency versus best practices.
- [ ] Identify and describe missing signing setup, startup stability, and rollout controls, including recommended guardrails.
- [ ] List required/optional checks for this focus area and highlight which are missing or should be upgraded.
- [ ] Provide at least two irritation/failure scenarios with expected behavior/outcomes for release hardening.
- [ ] Capture dependencies to other focus areas (e.g., testing, crash monitoring) noted during analysis.

## Risks
- Release build signing uses debug key—production rollout to Play Store will fail or be rejected, and there’s no key-rotation/secure storage strategy documented.
- No mapping-file handling or automated verification of ProGuard rules; a reflection-dependent parser (Retrofit/TikXML) could be stripped, causing runtime crashes.
- Lack of explicit startup/checkpoint monitoring (e.g., crash reporting toggles) means rollout risk controls are not defined; regression here could go unnoticed until users report.
