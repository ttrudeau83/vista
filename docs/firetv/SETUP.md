# Fire TV setup (Fire TV Stick 4K Max 2nd Gen, Fire OS 8.1.8.6)

The same Vista APK runs on the onn and the Fire TV. `data/system/Platform.kt` handles the differences at runtime.

## One-time device prep
1. Settings → My Fire TV → About → press Select on the device name 7 times. Developer Options appears.
2. Developer Options → **ADB Debugging: On**.
3. Note the IP (About → Network). It is currently `192.168.0.101`. A DHCP reservation on the router keeps it fixed.
4. From the repo root: `.\.tools\android-sdk\platform-tools\adb.exe connect 192.168.0.101:5555`, then accept **Always allow** on the TV.

## Install / update
```powershell
.\scripts\firetv\deploy.ps1 -ActivateHome   # first time: build, install, enable the self-healing Home redirect
.\scripts\firetv\deploy.ps1                 # later updates
.\scripts\firetv\deploy.ps1 -SkipBuild      # reinstall the last build
```
The Nuvio fork (`..\nuvio-vista`) is built separately. **The Fire TV is 32-bit only:** install `app-full-armeabi-v7a-release.apk`.

## How Home works on Fire OS
Fire OS never passes the Home key to apps, and it ignores the HOME role. When Amazon's home (`com.amazon.tv.launcher/.ui.HomeActivity_vNext`, or `com.amazon.firehomestarter`) comes to the front, Vista's accessibility service opens Vista over it. Because Nuvio is home, Vista then hands off to the Nuvio fork. Expect a brief glimpse of Amazon's screen.

Fire OS has no real Accessibility settings page, so Vista holds `WRITE_SECURE_SETTINGS` (ADB-granted, user-approved 2026-11-27). It turns its own redirect back on whenever it finds it off: at boot, after an update, and each time Vista opens.

## Differences from the onn
| Feature | Fire TV |
| --- | --- |
| Home redirect | Self-heals (onn: user toggles it in Settings) |
| Freeplay tab | Hidden (Google-only) |
| Hero blur | Dimmed instead (blur needs Android 12+) |
| Continue Watching / content rows | Fire OS never sends `INITIALIZE_PROGRAMS`, so Vista sends it at startup (`ChannelInitializer`). Nuvio's rows then appear. SmartTube publishes only when its own "channels" setting is on. |
| Rail | Same on both TVs: Search, Nuvio, Switch profiles, SmartTube, YouTube Kids, Music, Photos, Apps, Settings (Freeplay only on Google TV) |
| YouTube | `com.amazon.firetv.youtube` |
| Accessibility quick tile | Hidden (it opens a dummy page) |

Device changes and their undo commands: `CHANGES.md`. Full undo: `RECOVERY.md`.
