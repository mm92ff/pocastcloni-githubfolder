param(
    [string]$InputFile,
    [string]$OutputFile = "benchmark-reports/latest.md"
)

$ErrorActionPreference = "Stop"
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "../..")

if (-not $InputFile) {
    $InputFile = Get-ChildItem -Path (Join-Path $repoRoot "benchmark/build") -Recurse -Filter "*benchmarkData.json" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}

if (-not $InputFile -or -not (Test-Path $InputFile)) {
    throw "No benchmarkData.json found. Run :benchmark:connectedBenchmarkAndroidTest first."
}

$data = Get-Content $InputFile -Raw | ConvertFrom-Json
$outputPath = Join-Path $repoRoot $OutputFile
New-Item -ItemType Directory -Force -Path (Split-Path $outputPath) | Out-Null

$lines = [System.Collections.Generic.List[string]]::new()
$lines.Add("# Local Benchmark Report")
$lines.Add("")
$lines.Add("- Device: $($data.context.build.model), API $($data.context.build.version.sdk)")
$lines.Add("- Compilation: $($data.context.compilationMode)")
$lines.Add("- Source: ``$InputFile``")
$lines.Add("")
$lines.Add("| Benchmark | Metric | P50 | P90 | P95 | P99 | Threshold misses |")
$lines.Add("|---|---|---:|---:|---:|---:|---:|")

foreach ($benchmark in $data.benchmarks) {
    foreach ($metricProperty in $benchmark.sampledMetrics.PSObject.Properties) {
        $metric = $metricProperty.Value
        $samples = @($metric.runs | ForEach-Object { $_ })
        $threshold = if ($metricProperty.Name -eq "frameOverrunMs") { 0.0 } else { 16.67 }
        $longFrames = @($samples | Where-Object { [double]$_ -gt $threshold }).Count
        $lines.Add(
            "| $($benchmark.className.Split('.')[-1]).$($benchmark.name) | $($metricProperty.Name) | " +
            "$([math]::Round($metric.P50, 2)) | $([math]::Round($metric.P90, 2)) | " +
            "$([math]::Round($metric.P95, 2)) | $([math]::Round($metric.P99, 2)) | " +
            "$longFrames / $($samples.Count) (> $threshold ms) |"
        )
    }

    foreach ($metricProperty in $benchmark.metrics.PSObject.Properties) {
        $metric = $metricProperty.Value
        $lines.Add("")
        $lines.Add(
            "- **$($benchmark.name) / $($metricProperty.Name):** min $([math]::Round($metric.minimum, 2)), " +
            "median $([math]::Round($metric.median, 2)), max $([math]::Round($metric.maximum, 2)), " +
            "CoV $([math]::Round($metric.coefficientOfVariation, 3))"
        )
    }
}

$lines.Add("")
$lines.Add("> Emulator values are local baselines. Full composition tracing adds overhead; do not treat them as universal device limits.")
$lines | Set-Content -Path $outputPath -Encoding utf8
Write-Output $outputPath
