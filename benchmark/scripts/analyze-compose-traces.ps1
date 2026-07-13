param(
    [string]$TraceDirectory = "benchmark/build",
    [string]$TraceProcessor = "$env:LOCALAPPDATA/Pocastcloni/perfetto/trace_processor",
    [string]$OutputFile = "benchmark-reports/compose-recomposition.md",
    [switch]$EnforceBudgets
)

$ErrorActionPreference = "Stop"
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "../..")
$traceRoot = Join-Path $repoRoot $TraceDirectory
$queryFile = Join-Path $repoRoot "benchmark/trace-queries/recomposition-summary.sql"
$outputPath = Join-Path $repoRoot $OutputFile
$traces = @(Get-ChildItem -Path $traceRoot -Recurse -Filter "*.perfetto-trace" | Sort-Object Name)

if (-not (Test-Path $TraceProcessor)) {
    throw "Trace Processor not found at $TraceProcessor. See benchmark/README.md."
}
if ($traces.Count -eq 0) {
    throw "No Perfetto traces found below $traceRoot."
}

function Invoke-TraceQuery([string]$Trace) {
    if ([System.IO.Path]::GetExtension($TraceProcessor) -eq ".exe") {
        return & $TraceProcessor query -f $queryFile $Trace 2>$null
    }
    return & python $TraceProcessor query -f $queryFile $Trace 2>$null
}

$rows = @(
    foreach ($trace in $traces) {
        $row = Invoke-TraceQuery -Trace $trace.FullName | ConvertFrom-Csv
        $row | Add-Member -NotePropertyName trace -NotePropertyValue $trace.Name
        $row
    }
)

New-Item -ItemType Directory -Force -Path (Split-Path $outputPath) | Out-Null
$lines = [System.Collections.Generic.List[string]]::new()
$lines.Add("# Compose Recomposition Report")
$lines.Add("")
$lines.Add("| Trace | Window ms | Recomposer frames | Actual slices | Mini progress | Mini shell | Home | Full screen | Controls | Cover | Time labels | Detail | Layout |")
$lines.Add("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
foreach ($row in $rows) {
    $lines.Add(
        "| $($row.trace) | $($row.window_ms) | $($row.recompose_frames) | $($row.actual_recompose_slices) | " +
        "$($row.mini_progress) | $($row.mini_player) | $($row.home_screen) | $($row.full_player_screen) | " +
        "$($row.full_controls) | $($row.full_metadata_cover) | $($row.full_time_labels) | " +
        "$($row.detail_screen) | $($row.measure_layout) |"
    )
}
$lines.Add("")
$lines.Add("> Counts cover only the Macrobenchmark `measureBlock` and only the PocastCloni process.")
$lines | Set-Content -Path $outputPath -Encoding utf8
$rows | ConvertTo-Json -Depth 3 | Set-Content -Path ([System.IO.Path]::ChangeExtension($outputPath, ".json")) -Encoding utf8

if ($EnforceBudgets) {
    $violations = [System.Collections.Generic.List[string]]::new()
    foreach ($row in $rows) {
        if ($row.trace -like "*miniPlayerIdlePlayback*") {
            $unexpected =
                [int]$row.actual_recompose_slices +
                [int]$row.mini_progress +
                [int]$row.mini_player +
                [int]$row.mini_content +
                [int]$row.home_screen +
                [int]$row.podcast_grid +
                [int]$row.measure_layout
            if ($unexpected -ne 0) {
                $violations.Add("$($row.trace): MiniPlayer idle recomposed or relaid out static UI ($unexpected events)")
            }
        }

        if ($row.trace -like "*fullPlayerPlaybackAndPauseResume*") {
            # Pause/resume can publish request, player-state, and playback-state transitions.
            if (
                [int]$row.full_player_screen -gt 3 -or
                [int]$row.full_controls -gt 3 -or
                [int]$row.full_metadata_cover -gt 3 -or
                [int]$row.detail_screen -gt 3 -or
                [int]$row.full_time_labels -gt 8 -or
                [int]$row.measure_layout -gt 8
            ) {
                $violations.Add("$($row.trace): FullPlayer exceeded the pause/resume recomposition budget")
            }
        }
    }
    if ($violations.Count -gt 0) {
        throw "Recomposition budget failed:`n$($violations -join "`n")"
    }
}

Write-Output $outputPath
