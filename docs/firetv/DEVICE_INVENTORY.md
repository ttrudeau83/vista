# Device inventory: Fire TV Stick 4K Max (2nd Gen)

Captured 2026-11-27 over ADB (192.168.0.101:5555) with read-only commands only. Raw output: `docs/firetv/device-snapshots/snapshot-20261127-171927.txt` (regenerate with `scripts/firetv/snapshot.ps1`).
**No device settings, packages, or permissions were changed.**

## Hardware / build (Confirmed)
| Property | Value |
| --- | --- |
| Model / device | `AFTKRT` / `karat` |
| Fire OS | 8.1.8.6 (RS8186/3977), build `RS8186.3977N` |
| Android | 11, **API 30** |
| SoC / cores | MediaTek `mt8696`, 4 cores |
| RAM | 1.71 GB visible; ~490 MB available at idle with Settings open |
| Display | Physical 3840×2160; **UI 1920×1080 override, density 320** (960×540 dp, the same layout as a 1080p Google TV) |
| Fire TV Home | `com.amazon.tv.launcher` 7330553.1 |

## Home / launcher wiring (Confirmed)
- `HOME` resolves to **`com.amazon.tv.launcher/.ui.HomeActivity_vNext`** (priority 950).
- Other HOME activities: `com.amazon.firehomestarter/.HomeStarterActivity` (priority 1) and `com.amazon.tv.settings.v2/.system.FallbackHome` (priority −1000; this is the safety net if no other HOME is available).
- **HOME role holder: `com.amazon.firehomestarter`**. The role system exists; whether reassigning it changes Home-button behavior is Unknown (option D test).
- `cmd role get-role-holders` doesn't exist on this build; use `dumpsys role` instead.
- No disabled packages. No component overrides except the launcher's default `enabledComponents: HomeActivity_vNext`.

## Accessibility (Confirmed)
- `enabled_accessibility_services` = `null`, `accessibility_enabled` = 0, no bound services. Two services are installed (Amazon's own).
- **`android.settings.ACCESSIBILITY_SETTINGS` resolves to `com.amazon.tv.settings.v2/.compliance.CTSDummyIntentHandler`**, a dummy page. There is **no on-device way** to switch on a third-party accessibility service. It needs either the app holding `WRITE_SECURE_SETTINGS` (granted once over ADB) or an ADB `settings put secure` command.

## Remote / input (Confirmed)
- Input devices: `Amazon Fire TV Remote`, `kcmouse`, `WOW_INPUT_DEVICE`, `WOBLE_INPUT_DEVICE`, `amazon_touch`, `hdmipower`.
- Key codes of the remote's extra buttons are not mapped yet. Map them with `getevent -l /dev/input/event5` while pressing each button.

## Media / Nuvio (Confirmed)
- Nuvio installed: **`com.nuvio.app` version 0.4.2 (code 106)**, minSdk 24, targetSdk 36. This is the Play-flavor package and quite old. The onn's official app was 1.0.0, and the fork is based on 1.1.0.
- **The deep link works:** `nuvio://meta?type=movie&id=tt0133093` resolves to `com.nuvio.app/.MainActivity`.
- It has no `LEANBACK_LAUNCHER` activity; Vista's discovery falls back to the standard launcher entry.
- The TV provider (`com.android.providers.tv`) exists, but **channels, preview programs, and watch-next are all empty**. Nothing on this device publishes Android TV channels, so Vista's Continue Watching and per-app content rows will be **empty on Fire TV** unless a newer Nuvio publishes there. This needs a test after updating Nuvio.
- Other apps: SmartTube (`org.smarttube.stable`), YouTube (`com.amazon.firetv.youtube`), Stremio, Emby, Steam Link, Peacock, Tubi, Pluto, PBS, Xbox Game Pass, MX Player, Downloader.

## Sensitive system packages present (never disable)
`com.amazon.device.messaging` (+ sdk libraries), `com.amazon.firehomestarter`, `com.amazon.tv.oobe`, `com.amazon.tv.settings.v2`, `com.amazon.tv.settings.core`, `com.amazon.tv.quicksettings.ui`, `com.amazon.device.software.ota`, `com.amazon.tv.forcedotaupdater.v2` (OTA; ask first).
