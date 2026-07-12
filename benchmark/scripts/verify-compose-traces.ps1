param(
    [string]$TraceDirectory = "benchmark/build",
    [string[]]$RequiredSlices = @("MiniPlayer", "FullPlayerScreen")
)

$ErrorActionPreference = "Stop"
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "../..")
$searchRoot = Join-Path $repoRoot $TraceDirectory
$traces = @(Get-ChildItem -Path $searchRoot -Recurse -Filter "*.perfetto-trace")

if ($traces.Count -eq 0) {
    throw "No Perfetto traces found below $searchRoot."
}

$missing = [System.Collections.Generic.List[string]]::new()
foreach ($slice in $RequiredSlices) {
    $found = $false
    foreach ($trace in $traces) {
        $bytes = [System.IO.File]::ReadAllBytes($trace.FullName)
        $text = [System.Text.Encoding]::UTF8.GetString($bytes)
        if ($text.Contains($slice)) {
            $found = $true
            break
        }
    }
    if (-not $found) {
        $missing.Add($slice)
    }
}

if ($missing.Count -gt 0) {
    throw "Compose trace slices missing: $($missing -join ', ')"
}

Write-Output "Verified $($RequiredSlices.Count) Compose slice names across $($traces.Count) Perfetto traces."
