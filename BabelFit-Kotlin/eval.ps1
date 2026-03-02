<#
.SYNOPSIS
    BabelFit eval loop: run a sample CLI, analyze its trace, and output feedback.

.DESCRIPTION
    Runs one of the BabelFit sample CLIs (dnd, json-editor, customer-support) with
    optional scripted input, exports a trace, then runs the trace-viewer to generate
    an analysis report. Use -Compare to diff against the most recent previous trace.

.PARAMETER Sample
    Which sample to run: dnd, json-editor, or customer-support.

.PARAMETER Script
    Path to a script file with one prompt per line (required for json-editor and customer-support).

.PARAMETER TraceDir
    Directory for trace and report output (default: eval/traces).

.PARAMETER Agent
    Use agent mode for json-editor (ignored for other samples).

.PARAMETER Vendor
    AI vendor for trace analysis: openai, anthropic, gemini (default: openai).

.PARAMETER Model
    Model name for trace analysis (default: vendor default).

.PARAMETER Compare
    Compare with the most recent previous trace for this sample.

.PARAMETER SkipAnalysis
    Skip the trace analysis step — only run the sample and export the trace.

.EXAMPLE
    .\eval.ps1 -Sample json-editor -Script eval/scripts/json-editor-basic.txt
    .\eval.ps1 -Sample json-editor -Script eval/scripts/json-editor-basic.txt -Agent -Compare
    .\eval.ps1 -Sample dnd
    .\eval.ps1 -Sample customer-support -Script eval/scripts/customer-support-basic.txt
#>

param(
    [ValidateSet("dnd", "json-editor", "customer-support")]
    [string]$Sample = "json-editor",

    [string]$Script,

    [string]$TraceDir = "eval/traces",

    [switch]$Agent,

    [string]$Vendor = "openai",

    [string]$Model,

    [switch]$Compare,

    [switch]$SkipAnalysis
)

$ErrorActionPreference = "Stop"

# Resolve paths relative to this script's directory (BabelFit-Kotlin/)
$scriptRoot = $PSScriptRoot
$gradlew = Join-Path $scriptRoot "gradlew.bat"
$traceDirFull = Join-Path $scriptRoot $TraceDir

# Create trace directory
if (-not (Test-Path $traceDirFull)) {
    New-Item -ItemType Directory -Path $traceDirFull -Force | Out-Null
}

# Build trace filename
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$traceFile = "$Sample-$timestamp.btrace.json"
$tracePath = Join-Path $traceDirFull $traceFile
$reportFile = "$Sample-$timestamp-report.md"
$reportPath = Join-Path $traceDirFull $reportFile

# Map sample names to Gradle task names
$sampleTaskMap = @{
    "dnd" = "samples-dnd"
    "json-editor" = "samples-json-editor"
    "customer-support" = "samples-customer-support"
}
$sampleTask = $sampleTaskMap[$Sample]

# ─── Step 1: Run the sample CLI ────────────────────────────────────
Write-Host ""
Write-Host "═══════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  BabelFit Eval Loop" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  Sample:  $Sample"
Write-Host "  Trace:   $tracePath"
if ($Script) { Write-Host "  Script:  $Script" }
if ($Agent)  { Write-Host "  Mode:    Agent" }
Write-Host ""

# Build CLI arguments
$cliArgs = @()

if ($Sample -eq "dnd") {
    # DnD is fully automated, only needs --trace
    $cliArgs += "--trace"
    $cliArgs += $tracePath
} else {
    # json-editor and customer-support need --script
    if (-not $Script) {
        # Use default script if available
        $defaultScript = Join-Path $scriptRoot "eval/scripts/$Sample-basic.txt"
        if (Test-Path $defaultScript) {
            $Script = $defaultScript
            Write-Host "  Using default script: $Script" -ForegroundColor Yellow
        } else {
            Write-Error "No --Script provided and no default found at $defaultScript"
            exit 1
        }
    }

    # Resolve script path (could be relative)
    if (-not [System.IO.Path]::IsPathRooted($Script)) {
        $Script = Join-Path $scriptRoot $Script
    }

    $cliArgs += "--script"
    $cliArgs += $Script
    $cliArgs += "--trace"
    $cliArgs += $tracePath

    if ($Sample -eq "json-editor" -and $Agent) {
        $cliArgs += "--agent"
    }
}

$argsString = ($cliArgs | ForEach-Object { "`"$_`"" }) -join " "

Write-Host "  Running: gradlew :${sampleTask}:cli:run --args=`"$argsString`"" -ForegroundColor DarkGray
Write-Host ""

$gradleCmd = "& `"$gradlew`" -p `"$scriptRoot`" :${sampleTask}:cli:run --args=`"$argsString`" --console=plain 2>&1"
$sampleOutput = Invoke-Expression $gradleCmd
$sampleExitCode = $LASTEXITCODE

# Print sample output
$sampleOutput | ForEach-Object { Write-Host $_ }

if ($sampleExitCode -ne 0) {
    Write-Host ""
    Write-Error "Sample CLI failed with exit code $sampleExitCode"
    exit $sampleExitCode
}

# Verify trace was created
if (-not (Test-Path $tracePath)) {
    Write-Error "Trace file not created at $tracePath"
    exit 1
}

$traceSize = (Get-Item $tracePath).Length
Write-Host ""
Write-Host "  Trace exported: $tracePath ($traceSize bytes)" -ForegroundColor Green

if ($SkipAnalysis) {
    Write-Host ""
    Write-Host "  EVAL COMPLETE (analysis skipped)" -ForegroundColor Cyan
    Write-Host "  trace=$tracePath"
    exit 0
}

# ─── Step 2: Find previous trace for comparison ───────────────────
$compareArg = ""
if ($Compare) {
    $previousTraces = Get-ChildItem -Path $traceDirFull -Filter "$Sample-*.btrace.json" |
        Where-Object { $_.Name -ne $traceFile } |
        Sort-Object Name -Descending |
        Select-Object -First 1

    if ($previousTraces) {
        $previousPath = $previousTraces.FullName
        Write-Host "  Comparing with: $($previousTraces.Name)" -ForegroundColor Yellow
        $compareArg = "--compare `"$previousPath`""
    } else {
        Write-Host "  No previous trace found for comparison" -ForegroundColor Yellow
    }
}

# ─── Step 3: Run trace analysis ──────────────────────────────────
Write-Host ""
Write-Host "  Analyzing trace..." -ForegroundColor Cyan

$viewerArgs = "`"$tracePath`" --report --vendor $Vendor"
if ($Model) { $viewerArgs += " --model $Model" }
$viewerArgs += " --output `"$reportPath`""
if ($compareArg) { $viewerArgs += " $compareArg" }

Write-Host "  Running: gradlew :samples-trace-viewer:cli:run --args=`"$viewerArgs`"" -ForegroundColor DarkGray

$viewerCmd = "& `"$gradlew`" -p `"$scriptRoot`" :samples-trace-viewer:cli:run --args=`"$viewerArgs`" --console=plain 2>&1"
$viewerOutput = Invoke-Expression $viewerCmd
$viewerExitCode = $LASTEXITCODE

$viewerOutput | ForEach-Object { Write-Host $_ }

if ($viewerExitCode -ne 0) {
    Write-Host ""
    Write-Warning "Trace analysis failed (exit code $viewerExitCode). Trace is still available at $tracePath"
    exit $viewerExitCode
}

# ─── Step 4: Output the report ───────────────────────────────────
if (Test-Path $reportPath) {
    Write-Host ""
    Write-Host "═══════════════════════════════════════════════════════" -ForegroundColor Cyan
    Write-Host "  ANALYSIS REPORT" -ForegroundColor Cyan
    Write-Host "═══════════════════════════════════════════════════════" -ForegroundColor Cyan
    Write-Host ""
    Get-Content $reportPath | Write-Host
    Write-Host ""
    Write-Host "═══════════════════════════════════════════════════════" -ForegroundColor Cyan
    Write-Host "  EVAL COMPLETE" -ForegroundColor Green
    Write-Host "  trace=$tracePath" -ForegroundColor DarkGray
    Write-Host "  report=$reportPath" -ForegroundColor DarkGray
    Write-Host "═══════════════════════════════════════════════════════" -ForegroundColor Cyan
} else {
    Write-Host ""
    Write-Warning "Report file not created at $reportPath"
    Write-Host "  EVAL COMPLETE (report missing)" -ForegroundColor Yellow
    Write-Host "  trace=$tracePath"
}
