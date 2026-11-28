# Fire TV device change log

Every change made to the Fire TV (192.168.0.101, `AFTKRT`, Fire OS 8.1.8.6), with how to undo it.
A snapshot is taken before and after each change: `docs/firetv/device-snapshots/`.

| Date | Change | Why | Undo |
| --- | --- | --- | --- |
| 2026-11-27 | Uninstalled user app `com.nuvio.app` 0.4.2 | User request: the old official Nuvio never worked on the stick. It is replaced by the `dev.drewett.vista.nuvio` fork. | Reinstall the official APK if ever wanted |
| 2026-11-27 | `pm disable-user --user 0 com.amazon.tv.forcedotaupdater.v2` | User request: no Amazon updates. This package forces/prompts OTA installs. | `adb shell pm enable com.amazon.tv.forcedotaupdater.v2` |
| 2026-11-27 | **Attempted, refused:** `pm disable-user com.amazon.device.software.ota` | Fire OS: `SecurityException: Cannot disable a protected package` | Nothing to undo |
| 2026-11-27 | Installed Vista `dev.drewett.vista` (release) | The launcher | `adb uninstall dev.drewett.vista` |
| 2026-11-27 | `pm grant dev.drewett.vista WRITE_SECURE_SETTINGS` + enabled `VistaAccessibilityService` | Home redirect with self-heal (user-approved). Measured: Vista in front 230–330 ms after Amazon home starts (3/3). | `RECOVERY.md` §1 |
| 2026-11-27 | `pm grant dev.drewett.vista READ_TV_LISTINGS` | Same grant as the onn: read SmartTube/Nuvio channels | `pm revoke …` |
| 2026-11-27 | Copied connections from the onn (`scripts/Transfer-Connections.ps1`): Music, Immich, calendar, LG TV, weather, appearance, and the Trakt setup/library (no Trakt sign-in) | One set of connections on both TVs | Clear Vista's data in Settings → Applications, or uninstall |

## Amazon updates: current state and remaining gap

- **Done:** the forced-update prompt and installer helper (`forcedotaupdater.v2`) is disabled.
- **Not blockable on the device without root:** `com.amazon.device.software.ota` (the OTA download and install service) is a protected package. It can still download and install updates, typically while the stick is idle.
- **To fully block updates, use the router.** Block the Amazon update hosts in your router's DNS or firewall, or in Pi-hole or AdGuard if you run one. Hostnames reported in community block lists (not verified by us):
  - `softwareupdates.amazon.com`
  - `updates.amazon.com`
  - `amzdigitaldownloads.edgesuite.net`
  - `amzdigital-a.akamaihd.net`

  Block these **only for the Fire TV's IP** if your router allows per-device rules; other Amazon devices and app downloads may share hosts. This change is reversible by removing the rule.
- **Check:** Settings → My Fire TV → About → Check for Updates should fail or find nothing. Re-check `getprop ro.build.version.name` after a few days (it should stay `Fire OS 8.1.8.6`).
- **Trade-off:** no security fixes. Some Amazon apps (Prime Video, Appstore) may eventually ask for a newer Fire OS.
