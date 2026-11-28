<#
.SYNOPSIS
  Read-only snapshot of the Fire TV state. Run before AND after any device change.
  Makes no changes to the device.
.EXAMPLE
  .\scripts\firetv\snapshot.ps1 -Device 192.168.0.101:5555
#>
param(
    [string]$Device = "192.168.0.101:5555",
    [string]$Adb = (Join-Path $PSScriptRoot "..\..\.tools\android-sdk\platform-tools\adb.exe"),
    [string]$OutDir = (Join-Path $PSScriptRoot "..\..\docs\firetv\device-snapshots")
)
$ErrorActionPreference = "Continue"
New-Item -ItemType Directory -Force $OutDir | Out-Null
$out = Join-Path $OutDir ("snapshot-{0:yyyyMMdd-HHmmss}.txt" -f (Get-Date))

function Section($title, [string]$cmd) {
    "`n===== $title  [adb shell $cmd]" | Out-File $out -Append -Encoding utf8
    & $Adb -s $Device shell $cmd 2>&1 | Out-File $out -Append -Encoding utf8
}

Section "Build"                 "getprop ro.product.model; getprop ro.product.device; getprop ro.build.version.name; getprop ro.build.version.release; getprop ro.build.version.sdk; getprop ro.build.display.id; getprop ro.build.version.incremental"
Section "Memory"                "cat /proc/meminfo | head -3"
Section "HOME resolution"       "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
Section "All HOME activities"   "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
Section "HOME role holder"      "cmd role get-role-holders android.app.role.HOME"
Section "Accessibility enabled" "settings get secure enabled_accessibility_services; settings get secure accessibility_enabled"
Section "Disabled packages"     "pm list packages -d"
Section "All packages"          "pm list packages -f"
Section "Third-party packages"  "pm list packages -3"
Section "Launcher components"   "dumpsys package com.amazon.tv.launcher | grep -iE 'versionName|disabledComponents|enabledComponents|HomeActivity' "
Section "Firehomestarter"       "dumpsys package com.amazon.firehomestarter | grep -iE 'versionName|enabled='"
Section "TV provider"           "pm list packages com.android.providers.tv; content query --uri content://android.media.tv/channel --projection _id 2>&1 | head -5"
Section "Foreground activity"   "dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity'"
Section "Input devices"         "getevent -pl 2>/dev/null | grep -E 'add device|name:'"

Write-Host "Snapshot written to $out"
