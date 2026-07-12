param(
    [switch]$FullTracing,
    [string[]]$BenchmarkClasses = @(
        "com.example.pocastcloni.benchmark.StartupBenchmark",
        "com.example.pocastcloni.benchmark.RenderingBenchmark",
        "com.example.pocastcloni.benchmark.PlayerRenderingBenchmark"
    )
)

$ErrorActionPreference = "Stop"
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "../..")
$gradle = Join-Path $repoRoot "gradlew.bat"
$adbCandidates = [System.Collections.Generic.List[string]]::new()
if ($env:ANDROID_HOME) {
    $adbCandidates.Add((Join-Path $env:ANDROID_HOME "platform-tools/adb.exe"))
}
if ($env:LOCALAPPDATA) {
    $adbCandidates.Add((Join-Path $env:LOCALAPPDATA "Android/Sdk/platform-tools/adb.exe"))
}
$adbCandidates = @($adbCandidates | Where-Object { Test-Path $_ })
$adb = $adbCandidates | Select-Object -First 1
if (-not $adb) {
    throw "adb.exe not found. Set ANDROID_HOME or install the Android SDK in the default location."
}
$resultRoot = Join-Path $repoRoot "benchmark-reports"
$rawResultRoot = Join-Path $resultRoot "raw"

New-Item -ItemType Directory -Force -Path $rawResultRoot | Out-Null

foreach ($class in $BenchmarkClasses) {
    & $adb uninstall "com.example.pocastcloni" 2>$null | Out-Null
    & $adb uninstall "com.example.pocastcloni.benchmark" 2>$null | Out-Null

    $arguments = @(
        ":benchmark:connectedBenchmarkAndroidTest",
        "-Pandroid.testInstrumentationRunnerArguments.class=$class",
        "--console=plain"
    )
    if ($FullTracing) {
        $arguments += "-PfullTracing=true"
    }

    & $gradle @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Benchmark class failed: $class"
    }

    $json = Get-ChildItem -Path (Join-Path $repoRoot "benchmark/build") -Recurse -Filter "*benchmarkData.json" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $json) {
        throw "No benchmark JSON produced for $class"
    }

    $simpleName = $class.Split('.')[-1]
    $rawCopy = Join-Path $rawResultRoot "$simpleName.json"
    Copy-Item $json.FullName $rawCopy -Force
    & (Join-Path $PSScriptRoot "summarize-benchmarks.ps1") `
        -InputFile $rawCopy `
        -OutputFile "benchmark-reports/$simpleName.md"
}

Write-Output "Benchmark reports: $resultRoot"
