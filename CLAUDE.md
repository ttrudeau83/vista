@AGENTS.md

# Vista: one launcher for two devices

This repo is the single source of truth for both TVs. **One APK** detects the platform at runtime (`data/system/Platform.kt`).

| Device | Address | OS | Notes |
| --- | --- | --- | --- |
| onn 4K Streaming Box (`YOC`) | 192.168.0.82 | Google TV, Android 14 / API 34 | Original target |
| Fire TV Stick 4K Max 2nd Gen (`AFTKRT`/`karat`) | 192.168.0.101:5555 | Fire OS 8.1.8.6, Android 11 / API 30 | Added 2026-11-27, branch `firetv-support` |

192.168.0.86 is an Echo Show 5 (LineageOS), not a TV. Don't deploy there.

Companion app: `..\nuvio-vista` (NuvioTV fork, GPL-3.0, branch `vista-shell`, package `dev.drewett.vista.nuvio`). It is the **main/home destination on both devices**. Keep it on the latest stable upstream tag.

## Tooling (no system-wide installs)
- ADB: `.tools\android-sdk\platform-tools\adb.exe`
- JDK 17: `C:\Program Files\Unity\Hub\Editor\6000.6.0f1\Editor\Data\PlaybackEngines\AndroidPlayer\OpenJDK`
- Build env and command: `docs/INVESTIGATION.md` ("Baseline build result"). Gradle 8.7 on Windows can hit transform-cache rename errors; that doc has the workaround.

## Fire TV rules (from the project brief, binding)
- No root, no bootloader work, only reversible changes. Use `pm disable-user`, never `pm uninstall` for system packages.
- Run `scripts/firetv/snapshot.ps1` (read-only) before and after any device change.
- Keep launcher install, Home activation, and debloat as separate steps. Every disabled package gets a description and a restore command in `docs/firetv/`.
- Never disable `com.amazon.device.messaging`, the whole `com.amazon.tv.launcher` package, `com.amazon.firehomestarter`, `com.amazon.tv.oobe`, Settings, Wi-Fi, Bluetooth, or remote pairing packages.
- Fire OS ignores the HOME role for the Home key. The redirect is `VistaAccessibilityService`.
- Fire's accessibility settings page is a dummy. **User-approved (2026-11-27):** on Fire OS only, Vista holds an ADB-granted `WRITE_SECURE_SETTINGS` and re-enables its own service (self-heal). On the onn, the original rule stands: the user enables it in Settings.
- Fire OS 8 is API 30, so Compose `Modifier.blur` (API 31+) does nothing there. Use the non-blur fallback.
- The TV provider is empty on Fire OS. Hide empty content rows; never show fake data.
- Home on Fire and FireStick Fixer are GPL-3.0. Reimplement ideas from them; don't copy their code into this MIT app.

## Fire TV docs
`docs/firetv/FEASIBILITY.md` (research), `DEVICE_INVENTORY.md`, `SETUP.md`, `RECOVERY.md`, `CHANGES.md` (device change log with restore commands).
