<#
.SYNOPSIS
    Build, install and launch SpotKofi on a connected device. The closest
    equivalent to `flutter run` for a native Android project.

.DESCRIPTION
    Native Android has no single command that builds, installs, launches and
    attaches logs, so this script chains the three Gradle/adb steps that do.

.PARAMETER Pair
    One-time wireless pairing. Pass the host:port shown under
    "Pair device with pairing code" on the phone. You will be prompted for the
    6 digit code.

.PARAMETER Connect
    Connect to an already-paired phone. Pass the host:port shown on the main
    Wireless debugging screen. Note this is a DIFFERENT port than pairing uses.

.PARAMETER Logs
    Stream the app's logcat after launching. Ctrl+C to stop.

.PARAMETER Release
    Build the release variant instead of debug.

.EXAMPLE
    .\run.ps1 -Pair 192.168.1.7:37129
.EXAMPLE
    .\run.ps1 -Connect 192.168.1.7:41235
.EXAMPLE
    .\run.ps1
.EXAMPLE
    .\run.ps1 -Logs
#>
param(
    [string]$Pair,
    [string]$Connect,
    [switch]$Logs,
    [switch]$Release
)

$ErrorActionPreference = 'Stop'

$AppId = if ($Release) { 'com.spotkofi.app' } else { 'com.spotkofi.app.debug' }
$Activity = 'com.spotkofi.app.MainActivity'
$Variant = if ($Release) { 'Release' } else { 'Debug' }

function Write-Step($msg) { Write-Host "`n>> $msg" -ForegroundColor Cyan }
function Write-Ok($msg) { Write-Host "   $msg" -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "   $msg" -ForegroundColor Yellow }

# --- Pairing / connecting -----------------------------------------------------
if ($Pair) {
    Write-Step "Pairing with $Pair"
    Write-Warn 'Enter the 6 digit code shown on your phone.'
    adb pair $Pair
    Write-Warn 'Now run: .\run.ps1 -Connect <host:port from the Wireless debugging screen>'
    exit $LASTEXITCODE
}

if ($Connect) {
    Write-Step "Connecting to $Connect"
    adb connect $Connect
}

# --- Device check -------------------------------------------------------------
Write-Step 'Checking for a device'
# adb emits CRLF on Windows, so each line keeps a trailing \r and an unanchored
# `device$` match fails. Trim before matching.
$devices = @(
    (adb devices) |
        Select-Object -Skip 1 |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ -match '^(\S+)\s+device$' } |
        ForEach-Object { $Matches[1] }
)

if ($devices.Count -eq 0) {
    Write-Host @"

   No device found.

   Wireless (first time):
     1. Phone: Settings > Developer options > Wireless debugging > ON
     2. Tap "Pair device with pairing code"
     3. .\run.ps1 -Pair <host:port shown in that dialog>
     4. .\run.ps1 -Connect <host:port on the main Wireless debugging screen>

   Wireless (already paired):
     .\run.ps1 -Connect <host:port>

   Xiaomi / HyperOS also needs, under Developer options:
     - USB debugging                      ON
     - Wireless debugging                 ON
     - Install via USB                    ON
     - USB debugging (Security settings)  ON

"@ -ForegroundColor Yellow
    exit 1
}

Write-Ok "Found: $($devices -join ', ')"

# --- Build + install ----------------------------------------------------------
# `install<Variant>` builds and pushes in one task, but it does not launch.
Write-Step "Building and installing ($Variant)"
& ".\gradlew.bat" "install$Variant" --console=plain
if ($LASTEXITCODE -ne 0) {
    Write-Host "`n   Build failed." -ForegroundColor Red
    exit $LASTEXITCODE
}

# --- Launch -------------------------------------------------------------------
Write-Step 'Launching'
adb shell am start -n "$AppId/$Activity" | Out-Null
Write-Ok "$AppId started"

# --- Logs ---------------------------------------------------------------------
if ($Logs) {
    Write-Step 'Streaming logs (Ctrl+C to stop)'
    $procId = (adb shell pidof -s $AppId).Trim()
    if ($procId) {
        adb logcat --pid=$procId
    } else {
        Write-Warn 'Could not resolve the pid; falling back to an unfiltered log.'
        adb logcat
    }
}
