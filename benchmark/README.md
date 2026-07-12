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
.\benchmark\scripts\analyze-compose-traces.ps1 -EnforceBudgets
```

The standard runner deliberately invokes each class separately. Macrobenchmark
temporarily writes traces to the device, and a single large suite can exhaust a
small AVD data partition. Reports are copied to the ignored
`benchmark-reports/` directory between runs.

## Trace Analysis

`verify-compose-traces.ps1` provides a lightweight automated assertion that
expected Compose names are embedded in generated traces. For detailed slice and
frame analysis, `analyze-compose-traces.ps1` restricts results to PocastCloni's
Macrobenchmark `measureBlock`. The SQL files can also be run directly with
Perfetto Trace Processor Shell or Android Studio Profiler.

Install the official Windows Trace Processor wrapper once:

```powershell
$toolDir = "$env:LOCALAPPDATA/Pocastcloni/perfetto"
New-Item -ItemType Directory -Force $toolDir
Invoke-WebRequest https://get.perfetto.dev/trace_processor `
  -OutFile "$toolDir/trace_processor"
python "$toolDir/trace_processor" --version
```

The wrapper downloads and verifies the official native binary for the current
platform on first use.

### First Recomposition Finding

Five five-second MiniPlayer traces initially showed ten executions of
`MiniPlayerProgressBar`, caused by reading the complete playback state in the
composition phase only to check whether a duration existed. Reading a distinct
`hasDuration` value removed those executions. The repeated trace then showed:

- zero actual recomposition slices during MiniPlayer idle playback
- zero `HomeScreen`, podcast-grid, MiniPlayer-shell, and layout executions
- frame-by-frame animation scheduling only for the draw-phase progress update

Five FullPlayer traces showed two executions of the static screen, controls,
cover metadata, and underlying detail screen. Those correspond to the measured
pause and resume actions. Time labels executed six or seven times in the
approximately 5.7-second window, as expected for second-level updates.

`-EnforceBudgets` turns these observations into local regression checks. It does
not constrain the animation-frame clock because draw-phase progress updates are
expected to run every frame.

## First Baseline Decision

The API 35 emulator showed high and host-sensitive frame durations, including
large variation during traced playback. Those values are retained as local
observations, not universal gates. A Baseline Profile is not added yet: startup
and scroll improvement must first be reproduced across at least three stable
runs, ideally including the P30, before adding another production artifact.
