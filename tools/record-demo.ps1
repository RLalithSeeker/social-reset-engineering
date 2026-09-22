<#
.SYNOPSIS
  Records the Social Reset block-overlay demo video from the emulator.

.DESCRIPTION
  Waits until the clean block overlay ("YouTube is blocked / Social Reset
  required.") is on screen, then captures a fixed-length MP4 via
  `adb shell screenrecord` and pulls it into demo/.

  Manual flow on the device (do this while the script waits):
    1. Open Social Reset, scroll to the bottom.
    2. Under "Debug test panel", tap "Seed YouTube block rule".
    3. Tap "Show sample block overlay".
  The script detects BlockOverlayActivity in the foreground and starts
  recording only then, so the video opens on the clean overlay.

.EXAMPLE
  .\tools\record-demo.ps1
  .\tools\record-demo.ps1 -Seconds 20 -Serial emulator-5554
#>
param(
    [string]$Serial = "emulator-5554",
    [int]$Seconds = 25,
    [string]$Adb = "E:\dev\android-sdk\platform-tools\adb.exe"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$demoDir = Join-Path $repoRoot "demo"
New-Item -ItemType Directory -Path $demoDir -Force | Out-Null

Write-Host "Checking device $Serial ..." -ForegroundColor Cyan
& $Adb -s $Serial wait-for-device
$boot = (& $Adb -s $Serial shell getprop sys.boot_completed).Trim()
if ($boot -ne "1") { throw "Device $Serial is not booted (sys.boot_completed=$boot)." }

Write-Host ""
Write-Host "Get the overlay on screen now:" -ForegroundColor Yellow
Write-Host "  1. Open Social Reset, scroll to the bottom."
Write-Host "  2. Under 'Debug test panel', tap 'Seed YouTube block rule'."
Write-Host "  3. Tap 'Show sample block overlay'."
Write-Host ""
Write-Host "Waiting for block overlay (BlockOverlayActivity in foreground) ..." -ForegroundColor Cyan

$found = $false
for ($i = 0; $i -lt 60; $i++) {
    $focus = & $Adb -s $Serial shell dumpsys window windows 2>$null |
        Select-String "mCurrentFocus" | Select-Object -First 1
    if ($focus -match "BlockOverlayActivity") { $found = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $found) { throw "Timed out waiting 120s for the block overlay. Show it on screen, then re-run." }

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$remote = "/sdcard/social-reset-demo.mp4"
$local = Join-Path $demoDir "social-reset-demo-$stamp.mp4"

Write-Host "Overlay detected - recording $Seconds s ..." -ForegroundColor Green
& $Adb -s $Serial shell screenrecord --time-limit $Seconds $remote
if ($LASTEXITCODE -ne 0) { throw "screenrecord failed (exit $LASTEXITCODE)." }

& $Adb -s $Serial pull $remote $local | Out-Null
& $Adb -s $Serial shell rm $remote | Out-Null

Write-Host ""
Write-Host "Saved: $local" -ForegroundColor Green
Write-Host "Play it back, confirm it opens on 'YouTube is blocked / Social Reset required.', then attach it to your submission."
