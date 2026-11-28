<#
.SYNOPSIS
  Build Vista and install it on a TV over ADB. Run from the repo root.
  The same APK works on the onn (Google TV) and the Fire TV.
.PARAMETER Device   ADB address. Default: the Fire TV.
.PARAMETER SkipBuild  Install the existing APK without rebuilding.
.PARAMETER ActivateHome  Fire TV only: grant WRITE_SECURE_SETTINGS so Vista keeps its Home
  redirect on (self-heal), and switch the redirect on now. Reversible: see docs/firetv/RECOVERY.md.
.EXAMPLE
  .\scripts\firetv\deploy.ps1                  # build + install + launch on the Fire TV
  .\scripts\firetv\deploy.ps1 -ActivateHome    # also make Home go to Vista
  .\scripts\firetv\deploy.ps1 -Device 192.168.0.82:5555   # onn
#>
param(
    [string]$Device = "192.168.0.101:5555",
    [switch]$SkipBuild,
    [switch]$ActivateHome
)
$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$adb = Join-Path $root ".tools\android-sdk\platform-tools\adb.exe"
$pkg = "dev.drewett.vista"
$service = "$pkg/$pkg.service.VistaAccessibilityService"

if (-not $SkipBuild) {
    $androidTools = 'C:\Program Files\Unity\Hub\Editor\6000.6.0f1\Editor\Data\PlaybackEngines\AndroidPlayer'
    $env:JAVA_HOME = "$androidTools\OpenJDK"
    $env:ANDROID_HOME = Join-Path $root ".tools\android-sdk"
    $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
    $env:GRADLE_USER_HOME = Join-Path $root ".gradle-user"
    Push-Location $root
    try { & .\gradlew.bat :app:assembleRelease --no-daemon --no-watch-fs --console=plain; if ($LASTEXITCODE) { throw "Build failed" } }
    finally { Pop-Location }
}

$apk = Join-Path $root "app\build\outputs\apk\release\app-release.apk"
& $adb connect $Device | Out-Null
& $adb -s $Device install -r $apk
if ($LASTEXITCODE) { throw "Install failed" }
# Same grant as the onn: lets Vista read other apps' published TV channels (SmartTube/Nuvio rows).
& $adb -s $Device shell pm grant $pkg android.permission.READ_TV_LISTINGS

if ($ActivateHome) {
    & $adb -s $Device shell pm grant $pkg android.permission.WRITE_SECURE_SETTINGS
    $current = (& $adb -s $Device shell settings get secure enabled_accessibility_services).Trim()
    if ($current -eq "null" -or [string]::IsNullOrWhiteSpace($current)) { $new = $service }
    elseif ($current -notmatch [regex]::Escape($service)) { $new = "$current`:$service" }
    else { $new = $current }
    & $adb -s $Device shell settings put secure enabled_accessibility_services $new
    & $adb -s $Device shell settings put secure accessibility_enabled 1
    Write-Host "Home redirect on: $(& $adb -s $Device shell settings get secure enabled_accessibility_services)"
}

& $adb -s $Device shell am start -n "$pkg/.MainActivity" | Out-Null
Write-Host "Installed and launched on $Device"
