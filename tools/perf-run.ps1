[CmdletBinding()]
param(
    [ValidateSet("six", "single")]
    [string]$Suite = "six",

    [ValidateSet("baseline", "optimized", "cache_only", "preload_only", "load_control_only")]
    [string]$Profile = "optimized",

    [int]$Runs = 1,

    [ValidateSet("EveryRun", "FirstRunOnly", "Never")]
    [string]$ClearMode = "EveryRun",

    [string]$PackageName = "com.example.flower_show",
    [string]$ActivityName = ".MainActivity",
    [string]$OutDir = "performance-runs",
    [int]$AutoDurationSec = 0,
    [string]$RepairSessionDir = "",
    [switch]$SkipBuild,
    [switch]$SkipInstall,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

function Resolve-Adb {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) {
        return $cmd.Source
    }

    $candidates = @()
    if ($env:ANDROID_HOME) {
        $candidates += Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
    }
    if ($env:LOCALAPPDATA) {
        $candidates += Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    }

    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) {
            return $candidate
        }
    }

    throw "adb.exe was not found. Set ANDROID_HOME or add platform-tools to PATH."
}

function Invoke-External {
    param(
        [string]$FilePath,
        [string[]]$Arguments,
        [string]$Description
    )

    Write-Host "==> $Description"
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Description failed with exit code $LASTEXITCODE"
    }
}

function Get-MetricValues {
    param(
        [string[]]$Lines,
        [string]$Name,
        [string]$Field = "durationMs"
    )

    $pattern = "metric name=$Name .*?$Field=([0-9]+)"
    @(
        $Lines | ForEach-Object {
            if ($_ -match $pattern) {
                [int64]$Matches[1]
            }
        }
    )
}

function Get-Stats {
    param([object[]]$Values)

    $values = @($Values | Where-Object { $_ -ne $null } | ForEach-Object { [double]$_ })
    if ($values.Count -eq 0) {
        return [ordered]@{
            count = 0
            avg = ""
            p50 = ""
            p95 = ""
            min = ""
            max = ""
        }
    }

    $sorted = @($values | Sort-Object)
    $p50Index = [math]::Floor(0.50 * ($sorted.Count - 1))
    $p95Index = [math]::Floor(0.95 * ($sorted.Count - 1))

    [ordered]@{
        count = $sorted.Count
        avg = [math]::Round(($sorted | Measure-Object -Average).Average, 1)
        p50 = [math]::Round($sorted[$p50Index], 1)
        p95 = [math]::Round($sorted[$p95Index], 1)
        min = [math]::Round($sorted[0], 1)
        max = [math]::Round($sorted[-1], 1)
    }
}

function Add-StatsToRow {
    param(
        [System.Collections.IDictionary]$Row,
        [string]$Prefix,
        [object[]]$Values
    )

    $stats = Get-Stats $Values
    foreach ($key in $stats.Keys) {
        $Row["${Prefix}_${key}"] = $stats[$key]
    }
}

function Set-StatsToRow {
    param(
        [System.Collections.IDictionary]$Row,
        [string]$Prefix,
        [System.Collections.IDictionary]$Stats
    )

    foreach ($key in $Stats.Keys) {
        $Row["${Prefix}_${key}"] = $Stats[$key]
    }
}

function Get-DiagnosticMetricStats {
    param(
        [string[]]$Lines,
        [string]$Name
    )

    $escapedName = [regex]::Escape($Name)
    $pattern = "^$escapedName\s+samples=([0-9]+)\s+avg=([0-9.]+)\s+p50=([0-9.]+)\s+p95=([0-9.]+)\s+min=([0-9.]+)\s+max=([0-9.]+)"
    foreach ($line in $Lines) {
        if ($line -match $pattern) {
            return [ordered]@{
                count = [int]$Matches[1]
                avg = [double]$Matches[2]
                p50 = [double]$Matches[3]
                p95 = [double]$Matches[4]
                min = [double]$Matches[5]
                max = [double]$Matches[6]
            }
        }
    }

    $null
}

function Add-MetricStatsToRow {
    param(
        [System.Collections.IDictionary]$Row,
        [string[]]$Lines,
        [string]$Prefix,
        [string]$MetricName,
        [string]$Field = "durationMs"
    )

    $diagnosticStats = Get-DiagnosticMetricStats -Lines $Lines -Name $MetricName
    if ($diagnosticStats) {
        Set-StatsToRow -Row $Row -Prefix $Prefix -Stats $diagnosticStats
        return
    }

    Add-StatsToRow $Row $Prefix (Get-MetricValues $Lines $MetricName $Field)
}

function Resolve-ExistingOrRawPath {
    param([string]$Path)

    if (Test-Path -LiteralPath $Path) {
        return (Resolve-Path -LiteralPath $Path).Path
    }

    return $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Path)
}

function Write-LogcatToFile {
    param(
        [string]$AdbPath,
        [string]$LogPath,
        [string]$PackageName
    )

    # Always create the file so later parsing/summary generation can proceed even when logcat is empty.
    Set-Content -LiteralPath $LogPath -Value @() -Encoding UTF8

    $logOutput = & $AdbPath logcat -d -v time FlowerPerf:D FlowerMetrics:D VideoPlayerManager:D "*:S" 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "logcat export failed for $PackageName, keeping empty log at $LogPath"
        return
    }

    if ($null -ne $logOutput) {
        @($logOutput) | Set-Content -LiteralPath $LogPath -Encoding UTF8
    }
}

function Get-RunMetadataFromName {
    param([string]$RunName)

    if ($RunName -match "^(baseline_cold|optimized_cold|cache_only|preload_only|load_control_only)(?:_(\d+))?$") {
        $suiteName = $Matches[1]
        $runIndex = if ($Matches[2]) { [int]$Matches[2] } else { 1 }
        $profileName = switch ($suiteName) {
            "baseline_cold" { "baseline" }
            "optimized_cold" { "optimized" }
            "cache_only" { "cache_only" }
            "preload_only" { "preload_only" }
            "load_control_only" { "load_control_only" }
            default { $suiteName }
        }

        return [pscustomobject]@{
            SuiteName = $suiteName
            ProfileName = $profileName
            RunIndex = $runIndex
        }
    }

    throw "Unable to infer run metadata from directory name '$RunName'."
}

function Test-ResultsCsvContainsRun {
    param(
        [string]$ResultsCsvPath,
        [string]$RunName
    )

    if (-not (Test-Path -LiteralPath $ResultsCsvPath)) {
        return $false
    }

    $existingRows = @(Import-Csv -LiteralPath $ResultsCsvPath)
    return @($existingRows | Where-Object { $_.runName -eq $RunName }).Count -gt 0
}

function Append-ResultsRow {
    param(
        [pscustomobject]$Row,
        [string]$ResultsCsvPath
    )

    if (Test-ResultsCsvContainsRun -ResultsCsvPath $ResultsCsvPath -RunName $Row.runName) {
        Write-Host "Skipping duplicate results.csv row for $($Row.runName)"
        return
    }

    if (Test-Path -LiteralPath $ResultsCsvPath) {
        $Row | Export-Csv -LiteralPath $ResultsCsvPath -NoTypeInformation -Append -Encoding UTF8
    } else {
        $Row | Export-Csv -LiteralPath $ResultsCsvPath -NoTypeInformation -Encoding UTF8
    }
}

function Parse-PerfLog {
    param(
        [string]$LogPath,
        [string]$DiagnosticsPath = "",
        [string]$SuiteName,
        [string]$RunName,
        [string]$ProfileName,
        [int]$RunIndex,
        [bool]$ClearedData
    )

    $lines = @()
    if (Test-Path -LiteralPath $LogPath) {
        $lines += @(Get-Content -LiteralPath $LogPath -ErrorAction SilentlyContinue)
    }
    if ($DiagnosticsPath -and (Test-Path -LiteralPath $DiagnosticsPath)) {
        $lines += @(Get-Content -LiteralPath $DiagnosticsPath -ErrorAction SilentlyContinue)
    }
    $row = [ordered]@{
        timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
        suite = $SuiteName
        runName = $RunName
        profile = $ProfileName
        runIndex = $RunIndex
        clearedData = $ClearedData
        logPath = Resolve-ExistingOrRawPath $LogPath
    }

    Add-MetricStatsToRow $row $lines "startup_activity_on_create_ms" "startup_activity_on_create"
    Add-MetricStatsToRow $row $lines "startup_first_draw_ms" "startup_first_draw"
    Add-MetricStatsToRow $row $lines "startup_video_feed_ready_ms" "startup_video_feed_ready"
    Add-MetricStatsToRow $row $lines "video_first_frame_ms" "video_first_frame"
    Add-MetricStatsToRow $row $lines "video_ready_ms" "video_ready"
    Add-MetricStatsToRow $row $lines "video_buffering_ms" "video_buffering"
    Add-MetricStatsToRow $row $lines "quality_switch_ms" "quality_switch"
    Add-MetricStatsToRow $row $lines "feed_jank_percent" "feed_frame_jank_percent" "percent"
    Add-MetricStatsToRow $row $lines "feed_frame_max_ms" "feed_frame_max_ms"

    $firstFrames = @(
        $lines | ForEach-Object {
            if ($_ -match "metric name=video_first_frame durationMs=([0-9]+).*?reason=([^ ]+).*?source=([^ ]+)") {
                [pscustomobject]@{
                    DurationMs = [int64]$Matches[1]
                    Reason = $Matches[2]
                    Source = $Matches[3]
                }
            }
        }
    )
    Add-StatsToRow $row "video_first_frame_play_ms" @(
        $firstFrames | Where-Object { $_.Reason -eq "play" } | ForEach-Object { $_.DurationMs }
    )
    Add-StatsToRow $row "video_first_frame_play_preload_hit_ms" @(
        $firstFrames | Where-Object { $_.Reason -eq "play" -and $_.Source -eq "preload_hit" } | ForEach-Object { $_.DurationMs }
    )

    $cacheRows = @(
        $lines | ForEach-Object {
            if ($_ -match "cache_snapshot .*?hitRatioPercent=([0-9]+(?:\.[0-9]+)?).*?cachedBytes=([0-9]+).*?upstreamBytes=([0-9]+).*?totalBytes=([0-9]+).*?cacheIgnoredCount=([0-9]+)") {
                [pscustomobject]@{
                    HitRatioPercent = [double]$Matches[1]
                    CachedBytes = [int64]$Matches[2]
                    UpstreamBytes = [int64]$Matches[3]
                    TotalBytes = [int64]$Matches[4]
                    Ignored = [int64]$Matches[5]
                }
            }
        }
    )
    $lastCache = $cacheRows | Select-Object -Last 1
    $row["cache_snapshot_count"] = $cacheRows.Count
    $row["cache_hit_ratio_last_percent"] = if ($lastCache) { $lastCache.HitRatioPercent } else { "" }
    $row["cache_cached_bytes_last"] = if ($lastCache) { $lastCache.CachedBytes } else { "" }
    $row["cache_upstream_bytes_last"] = if ($lastCache) { $lastCache.UpstreamBytes } else { "" }
    $row["cache_total_bytes_last"] = if ($lastCache) { $lastCache.TotalBytes } else { "" }

    $qualityValues = Get-MetricValues $lines "quality_switch"
    $row["quality_switch_over_1000ms_count"] = @($qualityValues | Where-Object { $_ -gt 1000 }).Count
    $row["event_experiment_profile_count"] = @($lines | Select-String -SimpleMatch "name=experiment_profile").Count
    $row["event_cache_initialized_count"] = @($lines | Select-String -SimpleMatch "name=cache_initialized").Count
    $row["event_cache_disabled_count"] = @($lines | Select-String -SimpleMatch "name=cache_disabled").Count
    $row["event_preload_window_count"] = @($lines | Select-String -SimpleMatch "name=preload_window").Count
    $row["event_preload_disabled_count"] = @($lines | Select-String -SimpleMatch "name=preload_disabled").Count
    $row["event_preload_window_skipped_count"] = @($lines | Select-String -SimpleMatch "name=preload_window_skipped").Count
    $row["event_manual_quality_count"] = @($lines | Select-String -SimpleMatch "name=manual_quality").Count
    $row["event_auto_quality_count"] = @($lines | Select-String -SimpleMatch "name=auto_quality").Count

    [pscustomobject]$row
}

function Write-Summary {
    param(
        [pscustomobject]$Row,
        [string]$SummaryPath
    )

    $lines = @()
    $lines += "FlowerShow performance run summary"
    $lines += "runName=$($Row.runName)"
    $lines += "profile=$($Row.profile)"
    $lines += "clearedData=$($Row.clearedData)"
    $lines += "logPath=$($Row.logPath)"
    $lines += ""
    foreach ($property in $Row.PSObject.Properties) {
        $lines += "$($property.Name)=$($property.Value)"
    }
    $lines | Set-Content -LiteralPath $SummaryPath -Encoding UTF8
}

function Repair-ExistingSession {
    param(
        [string]$SessionDir,
        [string]$ResultsCsvPath
    )

    $resolvedSessionDir = Resolve-ExistingOrRawPath $SessionDir
    if (-not (Test-Path -LiteralPath $resolvedSessionDir)) {
        throw "RepairSessionDir does not exist: $SessionDir"
    }

    $runDirs = @(Get-ChildItem -LiteralPath $resolvedSessionDir -Directory | Sort-Object Name)
    if ($runDirs.Count -eq 0) {
        throw "No run directories found under $resolvedSessionDir"
    }

    $sessionRows = @()
    foreach ($runDir in $runDirs) {
        $metadata = Get-RunMetadataFromName -RunName $runDir.Name
        $logPath = Join-Path $runDir.FullName "flower_perf.log"
        $diagnosticsPath = Join-Path $runDir.FullName "diagnostics.txt"
        $summaryPath = Join-Path $runDir.FullName "summary.txt"

        if (-not (Test-Path -LiteralPath $logPath)) {
            Set-Content -LiteralPath $logPath -Value @() -Encoding UTF8
        }

        $row = Parse-PerfLog `
            -LogPath $logPath `
            -DiagnosticsPath $diagnosticsPath `
            -SuiteName $metadata.SuiteName `
            -RunName $runDir.Name `
            -ProfileName $metadata.ProfileName `
            -RunIndex $metadata.RunIndex `
            -ClearedData $true

        Write-Summary -Row $row -SummaryPath $summaryPath
        Append-ResultsRow -Row $row -ResultsCsvPath $ResultsCsvPath
        $sessionRows += $row
    }

    $sessionSummary = Join-Path $resolvedSessionDir "session-summary.csv"
    $sessionRows | Export-Csv -LiteralPath $sessionSummary -NoTypeInformation -Encoding UTF8
    Write-Host "Repair completed for $resolvedSessionDir"
    Write-Host "Session summary: $sessionSummary"
    Write-Host "Accumulated results: $ResultsCsvPath"
}

function New-Plan {
    if ($Suite -eq "six") {
        $items = @()
        for ($i = 1; $i -le 3; $i++) {
            $items += [pscustomobject]@{
                SuiteName = "baseline_cold"
                ProfileName = "baseline"
                RunIndex = $i
                ClearData = $true
            }
        }
        for ($i = 1; $i -le 3; $i++) {
            $items += [pscustomobject]@{
                SuiteName = "optimized_cold"
                ProfileName = "optimized"
                RunIndex = $i
                ClearData = $true
            }
        }
        return $items
    }

    $items = @()
    for ($i = 1; $i -le $Runs; $i++) {
        $clear = switch ($ClearMode) {
            "EveryRun" { $true }
            "FirstRunOnly" { $i -eq 1 }
            "Never" { $false }
        }
        $items += [pscustomobject]@{
            SuiteName = "${Profile}_${ClearMode}"
            ProfileName = $Profile
            RunIndex = $i
            ClearData = $clear
        }
    }
    $items
}

if (-not $env:JAVA_HOME -and (Test-Path -LiteralPath "D:\android-studio-app\jbr")) {
    $env:JAVA_HOME = "D:\android-studio-app\jbr"
}
if (-not $env:ANDROID_HOME -and $env:LOCALAPPDATA) {
    $defaultSdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    if (Test-Path -LiteralPath $defaultSdk) {
        $env:ANDROID_HOME = $defaultSdk
    }
}
if ($env:JAVA_HOME) {
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}
if ($env:ANDROID_HOME) {
    $env:Path = "$env:ANDROID_HOME\platform-tools;$env:Path"
}

$sessionStamp = Get-Date -Format "yyyyMMdd-HHmmss"
$rootOut = Join-Path (Resolve-Path ".").Path $OutDir
$resultsCsv = Join-Path $rootOut "results.csv"

if ($DryRun) {
    $plan = @(New-Plan)
    Write-Host "Plan:"
    $plan | Format-Table SuiteName, ProfileName, RunIndex, ClearData -AutoSize
    if ($RepairSessionDir) {
        Write-Host "Dry run repair target: $(Resolve-ExistingOrRawPath $RepairSessionDir)"
    }
    Write-Host "Dry run only. No app/build/adb actions executed."
    exit 0
}

if ($RepairSessionDir) {
    Repair-ExistingSession -SessionDir $RepairSessionDir -ResultsCsvPath $resultsCsv
    exit 0
}

$adb = Resolve-Adb
$plan = @(New-Plan)
$sessionDir = Join-Path $rootOut "manual-six-$sessionStamp"

Write-Host "Plan:"
$plan | Format-Table SuiteName, ProfileName, RunIndex, ClearData -AutoSize

New-Item -ItemType Directory -Force -Path $sessionDir | Out-Null

$devices = @(& $adb devices | Select-String -Pattern "device$" | ForEach-Object { $_.Line })
if ($devices.Count -ne 1) {
    throw "Expected exactly one adb device, found $($devices.Count). Check 'adb devices'."
}

if (-not $SkipBuild) {
    Invoke-External -FilePath ".\gradlew.bat" -Arguments @(":app:assembleDebug") -Description "Build debug APK"
}

if (-not $SkipInstall) {
    $apk = Join-Path (Resolve-Path ".").Path "app\build\outputs\apk\debug\app-debug.apk"
    Invoke-External -FilePath $adb -Arguments @("install", "-r", $apk) -Description "Install debug APK"
}

$component = "$PackageName/$ActivityName"
$completedRows = @()

foreach ($item in $plan) {
    $runName = "{0}_{1:00}" -f $item.SuiteName, $item.RunIndex
    $runDir = Join-Path $sessionDir $runName
    New-Item -ItemType Directory -Force -Path $runDir | Out-Null

    Write-Host ""
    Write-Host "============================================================"
    Write-Host "Run: $runName"
    Write-Host "Profile: $($item.ProfileName)"
    Write-Host "Clear data: $($item.ClearData)"
    Write-Host "============================================================"

    & $adb shell am force-stop $PackageName | Out-Null
    if ($item.ClearData) {
        Invoke-External -FilePath $adb -Arguments @("shell", "pm", "clear", $PackageName) -Description "Clear app data"
    }

    Invoke-External -FilePath $adb -Arguments @("logcat", "-c") -Description "Clear logcat"
    Invoke-External -FilePath $adb -Arguments @(
        "shell",
        "am",
        "start",
        "-W",
        "-S",
        "-n",
        $component,
        "--es",
        "perf_profile",
        $item.ProfileName
    ) -Description "Start app profile=$($item.ProfileName)"

    Write-Host ""
    Write-Host "Manual actions for this run:"
    Write-Host "  1. Stay on feed for about 10 seconds."
    Write-Host "  2. Swipe through about 30 videos."
    Write-Host "  3. Switch quality 3 times if quality options are available."
    Write-Host "  4. Search one keyword and open one result."

    if ($AutoDurationSec -gt 0) {
        Write-Host "Auto collection starts after $AutoDurationSec seconds..."
        Start-Sleep -Seconds $AutoDurationSec
    } else {
        Read-Host "Press Enter here after the phone actions are done"
    }

    Write-Host "Sending Home to trigger onPause diagnostics flush..."
    & $adb shell input keyevent KEYCODE_HOME | Out-Null
    Start-Sleep -Seconds 2

    $logPath = Join-Path $runDir "flower_perf.log"
    $reportPath = Join-Path $runDir "diagnostics.txt"
    $cacheListPath = Join-Path $runDir "cache-list.txt"
    $summaryPath = Join-Path $runDir "summary.txt"

    Write-LogcatToFile -AdbPath $adb -LogPath $logPath -PackageName $PackageName

    $reportOutput = & $adb exec-out run-as $PackageName cat cache/flower_performance_diagnostics.txt 2>&1
    if ($LASTEXITCODE -eq 0 -and (($reportOutput -join "`n") -notmatch "No such file")) {
        $reportOutput | Set-Content -LiteralPath $reportPath -Encoding UTF8
    } else {
        "diagnostics report not available" | Set-Content -LiteralPath $reportPath -Encoding UTF8
    }

    & $adb shell run-as $PackageName ls -la cache |
        Set-Content -LiteralPath $cacheListPath -Encoding UTF8

    & $adb shell am force-stop $PackageName | Out-Null

    $row = Parse-PerfLog `
        -LogPath $logPath `
        -DiagnosticsPath $reportPath `
        -SuiteName $item.SuiteName `
        -RunName $runName `
        -ProfileName $item.ProfileName `
        -RunIndex $item.RunIndex `
        -ClearedData ([bool]$item.ClearData)

    Write-Summary -Row $row -SummaryPath $summaryPath
    $completedRows += $row

    Append-ResultsRow -Row $row -ResultsCsvPath $resultsCsv

    Write-Host "Saved:"
    Write-Host "  $logPath"
    Write-Host "  $reportPath"
    Write-Host "  $summaryPath"
}

$sessionSummary = Join-Path $sessionDir "session-summary.csv"
$completedRows | Export-Csv -LiteralPath $sessionSummary -NoTypeInformation -Encoding UTF8

Write-Host ""
Write-Host "All runs completed."
Write-Host "Session summary: $sessionSummary"
Write-Host "Accumulated results: $resultsCsv"
