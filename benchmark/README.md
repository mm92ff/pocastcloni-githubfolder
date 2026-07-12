# Performance Benchmarks

The benchmark module measures the release-like, profileable `benchmark` build
of PocastCloni on a separate test APK. It does not ship with the production app.

## Reference Device

- AVD: Medium Phone
- Android: 15 / API 35
- CPU cores exposed to AVD: 4
- Compilation mode: run from APK (`CompilationMode.None`)
- Host-dependent emulator values are useful for local regression comparisons,
  not as absolute Huawei P30 Pro targets.

## Scenarios

- `StartupBenchmark`: 10 cold and 10 warm starts.
- `RenderingBenchmark`: main navigation plus Settings tabs and scrolling,
  each repeated five times.
- `PlayerRenderingBenchmark`: MiniPlayer idle playback, podcast-detail playback
  with scrolling, and FullPlayer pause/resume, each repeated five times.

Player scenarios import a deterministic local RSS feed from MockWebServer. The
cover and 15-second silent WAV are local too, so no benchmark depends on the
internet or an external podcast host.

## Commands

Run the standard frame and startup baseline:

```powershell
.\benchmark\scripts\run-benchmarks.ps1
```

Run a focused Composition Tracing session:

```powershell
.\gradlew.bat :benchmark:connectedBenchmarkAndroidTest -PfullTracing=true `
  "-Pandroid.testInstrumentationRunnerArguments.class=com.example.pocastcloni.benchmark.PlayerRenderingBenchmark"
.\benchmark\scripts\verify-compose-traces.ps1
```

The standard runner deliberately invokes each class separately. Macrobenchmark
temporarily writes traces to the device, and a single large suite can exhaust a
small AVD data partition. Reports are copied to the ignored
`benchmark-reports/` directory between runs.

## Trace Analysis

`verify-compose-traces.ps1` provides a lightweight automated assertion that
expected Compose names are embedded in generated traces. For detailed slice and
frame analysis, run the SQL files in `trace-queries/` with Perfetto Trace
Processor Shell or Android Studio Profiler.

Trace Processor Shell was not present in the local Android SDK during the first
baseline. The SQL is versioned and ready, but its CSV export is therefore not a
hard local test yet.

## First Baseline Decision

The API 35 emulator showed high and host-sensitive frame durations, including
large variation during traced playback. Those values are retained as local
observations, not universal gates. A Baseline Profile is not added yet: startup
and scroll improvement must first be reproduced across at least three stable
runs, ideally including the P30, before adding another production artifact.
