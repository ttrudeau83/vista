<#
.SYNOPSIS
  Copy Vista's connections from one TV to another (default: onn -> Fire TV).

.DESCRIPTION
  Copies Music (YouTube) sign-in, Immich, family calendar, LG TV pairing, weather location,
  appearance settings and favourites. Copies the Trakt app setup and library, but NOT its sign-in:
  Trakt rotates refresh tokens, so two TVs sharing one would sign each other out. Sign in to Trakt
  on the target once afterwards.

  How: each TV's secrets are encrypted with a key locked to that TV. A debug build of Vista (same
  package and signing key, so app data is kept) is installed on both TVs for a moment. The source
  decrypts, and the bytes are piped through `adb` straight into the target, which re-encrypts them
  with its own key. Nothing is written to this computer's disk. Release builds are then reinstalled.

  Note: Fire TV ADB (port 5555) is not encrypted on the LAN, so the secrets cross your home
  network in the clear for a moment. Run it on a trusted network.
.EXAMPLE
  .\scripts\Transfer-Connections.ps1
  .\scripts\Transfer-Connections.ps1 -SkipBuild
#>
param(
    [string]$Source = "192.168.0.82:5555",    # onn
    [string]$Target = "192.168.0.101:5555",   # Fire TV
    [switch]$SkipBuild
)
$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$adb = Join-Path $root ".tools\android-sdk\platform-tools\adb.exe"
$pkg = "dev.drewett.vista"
$authority = "content://$pkg.debug.transfer"
$debugApk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
$releaseApk = Join-Path $root "app\build\outputs\apk\release\app-release.apk"

function Invoke-Adb([string]$Device) { & $adb -s $Device @args; if ($LASTEXITCODE) { throw "adb failed on ${Device}: $args" } }

if (-not $SkipBuild) {
    $androidTools = 'C:\Program Files\Unity\Hub\Editor\6000.6.0f1\Editor\Data\PlaybackEngines\AndroidPlayer'
    $env:JAVA_HOME = "$androidTools\OpenJDK"; $env:ANDROID_HOME = Join-Path $root ".tools\android-sdk"
    $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME; $env:GRADLE_USER_HOME = Join-Path $root ".gradle-user"
    Push-Location $root
    try { & .\gradlew.bat :app:assembleDebug :app:assembleRelease --no-daemon --no-watch-fs --console=plain; if ($LASTEXITCODE) { throw "Build failed" } }
    finally { Pop-Location }
}
foreach ($apk in $debugApk, $releaseApk) { if (-not (Test-Path $apk)) { throw "Missing $apk (run without -SkipBuild)" } }
foreach ($d in $Source, $Target) { & $adb connect $d | Out-Null; if ((& $adb -s $d get-state 2>$null) -ne "device") { throw "$d is not connected" } }

try {
    Write-Host "Installing the transfer build on both TVs (app data is kept)..."
    Invoke-Adb $Source install -r $debugApk | Out-Null
    Invoke-Adb $Target install -r $debugApk | Out-Null

    Write-Host "Source has:"; Invoke-Adb $Source shell content call --uri $authority --method summary

    Write-Host "Copying $Source -> $Target ..."
    # cmd.exe pipes raw bytes between the two adb processes; nothing touches the disk.
    $pipe = "`"$adb`" -s $Source exec-out content read --uri $authority/export | `"$adb`" -s $Target shell -T content write --uri $authority/import"
    cmd /c $pipe
    if ($LASTEXITCODE) { throw "Transfer pipe failed" }

    $status = ""
    for ($i = 0; $i -lt 30; $i++) {
        $status = (& $adb -s $Target shell content call --uri $authority --method importStatus) -join ""
        if ($status -notmatch "receiving|idle") { break }
        Start-Sleep -Milliseconds 500
    }
    Write-Host "Target: $status"
    if ($status -notmatch "imported") { throw "Import did not complete: $status" }
    Write-Host "Target now has:"; Invoke-Adb $Target shell content call --uri $authority --method summary
}
finally {
    Write-Host "Reinstalling the release build on both TVs..."
    & $adb -s $Source install -r $releaseApk | Out-Null
    & $adb -s $Target install -r $releaseApk | Out-Null
    & $adb -s $Target shell am start -n "$pkg/.MainActivity" | Out-Null
}
Write-Host "Done. On the target, open Vista > Settings > Trakt to sign in once."
