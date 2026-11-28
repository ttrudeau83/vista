# Fire TV: undo and recovery

Nothing of Amazon's launcher has been disabled or removed, so every step below gets you back to the stock Fire TV home screen. They are ordered from least to most drastic. ADB address: `192.168.0.101:5555` (`.tools\android-sdk\platform-tools\adb.exe`).

## 1. Turn off the Home redirect (keep Vista installed)
Vista re-enables the redirect while it holds the permission, so remove the permission first:
```powershell
.\.tools\android-sdk\platform-tools\adb.exe -s 192.168.0.101:5555 shell pm revoke dev.drewett.vista android.permission.WRITE_SECURE_SETTINGS
.\.tools\android-sdk\platform-tools\adb.exe -s 192.168.0.101:5555 shell settings delete secure enabled_accessibility_services
```
(The stock value was `null`, with no services, per the 2026-11-27 snapshot.) Home now goes to Amazon's launcher again.

## 2. Uninstall Vista and the Nuvio fork
```powershell
.\.tools\android-sdk\platform-tools\adb.exe -s 192.168.0.101:5555 uninstall dev.drewett.vista
.\.tools\android-sdk\platform-tools\adb.exe -s 192.168.0.101:5555 uninstall dev.drewett.vista.nuvio
```
Uninstalling also removes the accessibility service and its permission.
**Without a computer:** hold **Home** on the remote → Settings → Applications → Manage Installed Applications → Vista → Uninstall.

## 3. Re-enable Amazon updates
```powershell
.\.tools\android-sdk\platform-tools\adb.exe -s 192.168.0.101:5555 shell pm enable com.amazon.tv.forcedotaupdater.v2
```
Also remove any router block of Amazon update hosts (`CHANGES.md`).

## 4. If ADB stops connecting
Settings → My Fire TV → Developer Options → ADB Debugging on. Check the IP under About → Network. Then run `adb connect <ip>:5555` and accept the prompt on the TV.

## 5. Last resort: factory reset
Settings → My Fire TV → Reset to Factory Defaults, or hold **Back + Right** on the remote for 10 seconds. This clears everything above, including sideloaded apps and disabled packages. You'll need to sign in to Amazon again.

Every device change and its undo command is listed in `CHANGES.md`.
