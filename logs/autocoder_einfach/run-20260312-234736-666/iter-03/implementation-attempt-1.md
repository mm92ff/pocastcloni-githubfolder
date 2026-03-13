# Implementation
## Changed Files
- `docs/release-hardening.md`: Added the requested release hardening doc covering current minify/shrink setup (`app/build.gradle.kts:32`), the ProGuard rules file, best practices, audit criteria, dependencies, and the required failure scenarios plus Kotlin guard example.

## What Was Fixed
- Captured the gap analysis for release hardening, signing, startup stability, and rollout risk controls along with the evaluation checklist that highlights missing signing configs, mapping uploads, and crash-report instrumentation.
- Documented required/optional checks, concrete next actions, and at least two irritation/failure scenarios with the expected behaviors so the implementation team can follow up without code edits.

## Checks Run
- Not run (not requested).

## Open Risks
- Release signing is still missing a `signingConfigs` block, so the release artifact would continue to use the debug key until addressed.
- No automation or documentation currently covers mapping file uploads or release crash telemetry gating, leaving obfuscated releases hard to troubleshoot.
