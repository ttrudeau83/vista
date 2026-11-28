# Phase 1: Technical feasibility report: Vista launcher on Fire TV

Date: 2026-11-27. Status: **research only. No device changes made. Waiting for approval.**

Target: Fire TV Stick 4K Max (2nd Gen, 2023), Fire OS 8.1.8.6, Fire TV Home 7330553.1.
Fire OS 8.x is based on Android 11 (API 30). The model is reportedly `AFTKRT`, codename "karat". Confirm with `getprop` once ADB is connected.

## How much to trust each finding

**Update 2026-11-27:** ADB is now connected (192.168.0.101). A read-only inventory confirmed several facts on the real device; see [DEVICE_INVENTORY.md](DEVICE_INVENTORY.md). The highlights:
- `AFTKRT`/`karat`, Fire OS 8.1.8.6, API 30, 1.7 GB RAM, UI at 1920×1080 @ 320 dpi.
- HOME resolves to `com.amazon.tv.launcher/.ui.HomeActivity_vNext`, and the HOME **role** is held by `com.amazon.firehomestarter`.
- **The accessibility settings page is a dummy** (`CTSDummyIntentHandler`). An ADB-granted `WRITE_SECURE_SETTINGS` or `settings put` is the only way to enable the redirect.
- Nuvio is `com.nuvio.app` 0.4.2, and the **`nuvio://meta` deep link resolves**.
- **The TV provider exists but is empty**, so Continue Watching rows have no data source today.

The ADB and redirect behavior itself (Home press, flash, boot, and wake) is still untested. Every claim below has one of these labels:

| Label | Meaning |
| --- | --- |
| **Confirmed (8.1.8.6)** | Seen on the target device. *(None yet.)* |
| **Reported (8.1.8.x)** | Public reports on Fire OS 8.1.7.4 to 8.1.8.5. 8.1.8.5 is the closest build reported. |
| **Reported (other)** | Reports from other models or from Fire OS 7. These may not apply here. |
| **Theoretical** | Follows from Android 11 behavior. Not observed on Fire OS. |
| **Unknown** | Needs a device test. |

---

## 1. Feasibility summary (answers to the 10 research questions)

| # | Question | Answer | Confidence |
| --- | --- | --- | --- |
| 1 | Can third-party launchers be installed over ADB? | Yes. `adb install` works on Fire OS for any APK, and the launcher shows up in Your Apps & Channels. | Reported (8.1.8.x), widely |
| 2 | Do the standard HOME intent and launcher chooser work? | **No, not in a useful way.** Fire OS sends the Home key to Amazon's launcher and ignores the "preferred home" setting. `cmd package set-home-activity` and `cmd role add-role-holder android.app.role.HOME` report success, but the Home button still opens Amazon home (seen on Fire OS 7). On Fire OS 8 these commands are untested but cheap and reversible to try. | Reported (other); Unknown on 8.1.8.6 |
| 3 | Can the Amazon launcher be bypassed or disabled without root? | **Bypassed:** yes, with an accessibility-service redirect. **Disabled:** partly. Some guides disable only the HOME *activity component* (`com.amazon.tv.launcher/.ui.HomeActivity_vNext`) and keep the package, because its Settings screens live inside it. One user reports a boot loop on this exact model after disabling a long list that included `com.amazon.tv.launcher` and `com.amazon.firehomestarter`. Fire OS also refuses to disable some packages ("protected package"). | Reported (8.1.8.x / this model), high risk |
| 4 | Can the Home button be redirected to a custom launcher? | Yes, as a **redirect**, not a true replacement. Fire OS never passes the Home key to apps: Amazon's key policy consumes it before accessibility key filters see it. Redirectors watch for Amazon home to come to the front and then launch the custom launcher over it. | Reported (8.1.8.5) |
| 5 | Does Home on Fire (or similar) work on this firmware? | Home on Fire lists Fire OS 8.1.7.4 to 8.1.8.2 as tested (on an Amazon Ember TV). Its fork FireStick Fixer lists 8.1.8.5 (on a Fire TV Stick). Neither lists 8.1.8.6 or the 4K Max 2nd Gen. They will probably work, but that is unproven. | Reported (8.1.8.x) |
| 6 | Are accessibility services, ADB commands, or other mechanisms needed? | **Yes, an accessibility service is needed.** Fire OS often hides or blanks Settings → Accessibility for third-party services. The reliable way to switch the service on is a one-time grant: `adb shell pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS`. The app can then enable its own service. An optional `READ_LOGS` grant enables a faster redirect (§2). | Reported (8.1.8.x) |
| 7 | Can the launcher start at boot and on wake? | Yes, through the same redirect. Amazon home comes up at boot and after waking from sleep, the service sees it, and it opens our launcher. Home on Fire and FireStick Fixer both offer launch at boot and on wake. Android 10+ blocks most background activity starts, but an app with a bound accessibility service is exempt. | Reported (8.1.8.x); Theoretical for the exemption |
| 8 | Does Amazon's launcher briefly appear during redirection? | **Yes, with the basic method:** about 250 ms on each Home press. FireStick Fixer reads the system activity log (it needs `READ_LOGS`), reacts in about 40 ms, and covers the screen, so no flash is visible. Disabling the Amazon HOME component (option C) would remove the flash entirely, but it is the risky route. | Reported (8.1.8.5) |
| 9 | Can Amazon updates undo the changes? | **Yes.** OTA updates can reset component enable/disable states and may clear `enabled_accessibility_services`. The onn box cleared it on its own once (logged as `pkg:android` on 2026-11-17). FireStick Fixer reports that the `pm grant` permissions survive updates. Fire OS forcibly re-enables some packages on boot (for example `com.amazon.vizzini`). The launcher must detect a dropped redirect and say so; Vista already has a banner for this. | Reported |
| 10 | Which Amazon packages can be disabled safely? | Only after taking an on-device inventory. The published lists come from Fire OS 7 on the 1st Gen 4K Max, and package names vary between builds. Never disable `com.amazon.device.messaging` (ADM): it breaks the main Settings menu. Never disable the whole `com.amazon.tv.launcher` package or `com.amazon.firehomestarter`. A candidate list is in §6. | Reported (other) |

**Bottom line:** we can make our launcher the home screen for every normal use: the Home button, boot, wake, and returning from apps. Amazon's launcher stays installed and runs underneath. With the `READ_LOGS` approach it should be invisible in practice. A true no-redirect replacement needs either the risky component disable or root, and root is out of scope.

---

## 2. Comparison of launcher-redirection approaches

| Option | How it works | Flash | Setup | Risk / reversibility | Status |
| --- | --- | --- | --- | --- | --- |
| **A. Accessibility window watcher** (Vista's existing `VistaAccessibilityService`; Home on Fire) | Watches `TYPE_WINDOW_STATE_CHANGED`. When Amazon's HOME activity comes to the front, it launches our launcher. | ~250 ms | Install, then one `pm grant WRITE_SECURE_SETTINGS` (or the toggle, if Fire shows one) | Low. Fully reversible by uninstalling. | Reported 8.1.7.4 to 8.1.8.5. **Already in our code.** |
| **B. A + activity-log watcher** (FireStick Fixer) | Also follows the `ActivityTaskManager` log for `START … cat=[android.intent.category.HOME]` and reacts before Amazon home draws. | ~40 ms, covered | Option A plus `pm grant READ_LOGS` | Low. Reversible. Uses more CPU while idle (log reader); needs measuring. | Reported 8.1.8.5 |
| **C. Disable the Amazon HOME component** (XDA "LTV Launcher" guide for this model; dkg-lt scripts) | `pm disable-user` on `com.amazon.tv.launcher/.ui.HomeActivity_vNext` (+ `firehomestarter`), then `set-home-activity` to ours | None, a real replacement | ADB, several steps | **High.** A boot loop is reported on this model with a broader disable list. An OTA can undo it. Reversible only if ADB is still reachable. | Reported (this model, unverified: the XDA guide returned 403 to us) |
| **D. Home role / set-home-activity only** | `cmd role add-role-holder android.app.role.HOME`, `cmd package set-home-activity` | n/a | ADB | None. Reversible. | Ignored on Fire OS 7. **Untested on 8.1.8.6**; a cheap first test. |
| **E. Third-party redirector apps** (Home on Fire, FireStick Fixer, Launcher Manager) | Same as A/B, as a separate app | Same as A/B | Extra app | Low. Two redirectors at once conflict (reported). | Home on Fire and FireStick Fixer are **GPL-3.0**. Launcher Manager's license and source are unclear. |
| **F. Root / temp-root exploits** (e.g. GhostLock, CVE-2026-43499) | Disable protected packages | None | Exploit | Not allowed by project rules | **Rejected** |

**Recommendation:** use **A in our own launcher**. It already exists in Vista. Then add **B's log trigger as an opt-in setting** if the flash bothers you. Try **D** once because it costs nothing. Keep **C** as a separate, explicitly approved experiment, only after a full recovery check, or skip it entirely.

Licensing: Home on Fire and FireStick Fixer are GPL-3.0, and Vista is MIT. We should **reimplement** the log-trigger idea from the published behavior and not copy their code. Otherwise the launcher must become GPL. (The Nuvio fork is already GPL; see §4.)

---

## 3. ADB on this setup: capabilities and restrictions

**State of this PC (2026-11-27):**
- No system-wide ADB, JDK, or Android SDK. The onn project has working copies we can reuse:
  - ADB 1.0.41 (37.0.1): `.tools\android-sdk\platform-tools\adb.exe` (this repo) (SDK platforms 34/36, build-tools 34/35)
  - JDK 17: Unity's bundled OpenJDK, `C:\Program Files\Unity\Hub\Editor\6000.6.0f1\Editor\Data\PlaybackEngines\AndroidPlayer\OpenJDK`
- ADB currently sees the **onn box** (192.168.0.82). An mDNS device at 192.168.0.86:5555 turned out to be an **Echo Show 5 running LineageOS**, not the Fire TV; I disconnected it.
- **The Fire TV did not appear.** Fire OS's network ADB (classic TCP on port 5555) usually does not advertise over mDNS, so we need its IP address. Its ADB debugging may also be off.

**Enable ADB on the Fire TV:** Settings → My Fire TV → About → press Select on the device name 7 times → Developer Options appears. In Developer Options, turn on ADB Debugging and Install Unknown Apps. The IP is under My Fire TV → About → Network. Then run `adb connect <ip>:5555` and choose **Allow** ("Always allow from this computer") on the TV.

| Capability (no root) | Expected on Fire OS 8 | Notes |
| --- | --- | --- |
| `install` / `uninstall` / `install -r` | Yes | Our main dev loop |
| `pm grant` for runtime and development permissions (`WRITE_SECURE_SETTINGS`, `READ_LOGS`, `READ_TV_LISTINGS`) | Yes (reported) | Survives reboots; reported to survive updates |
| `settings get/put secure …` | Yes | Can enable accessibility services directly |
| `pm disable-user --user 0 <pkg or component>` / `pm enable` | Mostly | Some Amazon packages are "protected". Fire re-enables some at boot. |
| `pm uninstall --user 0` system apps | Possible, but **we will not use it** | Project rule: disable only, never remove |
| `cmd package set-home-activity`, `cmd role` | Accepted; effect Unknown | Test D |
| `am start`, `input keyevent`, `dumpsys`, `logcat`, `getevent`, `screencap` | Yes | For diagnostics and automated focus tests |
| `appops set … SYSTEM_ALERT_WINDOW allow` | Likely | Needed only for Vista's notification overlays |
| `cmd notification allow_listener` | Unknown | Notification panel |
| Read the system Watch Next table (`ACCESS_ALL_EPG_DATA`) | **No** | Signature/privileged, same as on the onn |
| Intercept the Home key | **No** | Amazon's key policy consumes it first |
| Bootloader, recovery, `/system` writes | **No** | Out of scope |
| Block OTA updates | Only by disabling `com.amazon.tv.forcedotaupdater.v2` etc. | **Not recommended** (security). Your decision. |

---

## 4. Reuse review of the existing onn projects

Two sibling projects already exist:

**`..\Projects\tvLauncher`** is "Vista", a fork of `kierandrewett/vista` (MIT) published at `ttrudeau83/vista`. Its package is `dev.drewett.vista`, built with Kotlin and Compose for TV, minSdk 28 and targetSdk 34, at version 0.7.2. It includes:
- A tab/rail shell, an immersive hero, focus handling with a fixed focus position, app discovery through PackageManager (no hardcoded packages), favourites, launch history, quick settings on the MENU key, and DataStore settings.
- Optional Immich photos and screensaver, a family calendar, Trakt, music, weather, and LG TV input switching.
- `VistaAccessibilityService`, which is **already option A**, plus a `HomeRedirectBanner` that warns when the redirect is switched off.
- A Nuvio integration (`NuvioTitleLink` builds `nuvio://meta?type=movie|series&id=tt…` / `&tmdb=…`, verified against the NuvioTV 1.1.0 parser tests). It also reads Nuvio's Continue Watching preview channel through the TV provider, and hands off to and from the Nuvio fork using `dev.drewett.vista.extra.TAB` / `extra.NUVIO_ROUTE`.

**`..\Projects\nuvio-vista`** is a fork of NuvioTV 1.1.0 (GPL-3.0) on branch `vista-shell`, package `dev.drewett.vista.nuvio`. It replaces Nuvio's sidebar with the Vista rail and makes Nuvio "home" (Vista 0.6.0+). Your sign-in values are in its ignored `local.properties`.

So Phase 2 applies: the source exists, and we should **port it, not rewrite it**.

### Google TV dependencies that need Fire OS replacements

| Area in Vista | Problem on Fire OS 8 (API 30) | Fix |
| --- | --- | --- |
| `cmd role add-role-holder android.app.role.HOME` (README install step) | Probably ignored | Rely on the accessibility redirect (A/B) |
| `VistaAccessibilityService` enablement | No Accessibility toggle in Fire Settings | One-time `WRITE_SECURE_SETTINGS` grant, plus an in-app "Enable Home redirect" switch **(this needs your approval: it differs from the onn rule "Vista never re-enables it over ADB")** |
| **`Modifier.blur` in `HomeTab` hero** | Compose blur needs **Android 12 (API 31)**. On Fire OS 8 it does nothing, so the hero would show sharp posters. | Pre-blurred and downscaled bitmaps, or a gradient scrim. This would also help the jank measured on the onn. |
| `GoogleTvLaunchSession` / Freeplay, `com.google.android.apps.tv.launcherx` | Google-only | Move behind a `Platform` interface; Fire implementation uses `com.amazon.tv.launcher` |
| YouTube Kids → `com.google.android.youtube.tv` | Fire uses `com.amazon.firetv.youtube` (verify on device) | Resolve via PackageManager (already the pattern) |
| System shortcuts in Quick Settings (Wi-Fi, Bluetooth, display) | Google Settings intents differ | Fire OS settings intents (to inventory on device); fall back to the Amazon Settings root |
| TV provider rows (`READ_TV_LISTINGS`, preview channels) | **Unknown.** `com.android.providers.tv` exists on Fire OS, but Fire's UI doesn't use Android TV channels, so Nuvio may not publish to it there | Test with `adb shell content query`. If empty, hide rows honestly (no fake data). |
| Notification listener and overlay | Unknown | Feature-flag it; test |
| `KEYCODE_SETTINGS`, app buttons | The 4K Max 2nd Gen remote has different buttons | Map with `getevent` on the device |
| MediaRouter audio output | Limited routes | Keep, and degrade gracefully |

---

## 5. Proposed architecture

**Recommended: make this repo the Fire TV port of Vista**, with full history, so the onn work keeps flowing.

```
firetvlauncher/            (git: origin = new repo, upstream = tvLauncher / ttrudeau83/vista)
  app/                     Vista app module, package dev.drewett.vista (kept, so Nuvio-fork hand-off intents still work)
    platform/              NEW: Platform interface with FireOsPlatform and GoogleTvPlatform
                           (home redirect, stock launcher id, settings intents, known package aliases)
    service/               VistaAccessibilityService + optional log-trigger (option B, opt-in)
    ui/ data/ domain/      unchanged Vista layers: Home, Nuvio, Apps, Search, Settings
  scripts/                 PowerShell: env.ps1, connect.ps1, deploy.ps1 (build, install, launch),
                           activate-home.ps1 / deactivate-home.ps1, snapshot.ps1,
                           debloat.ps1 + restore (separate, opt-in, per-package)
  docs/                    feasibility, device inventory, setup, recovery, limitations
```

- **Navigation** (from the brief): Home · Nuvio · Apps · Search · Settings. This maps onto Vista's existing rail. On the onn, Nuvio became Home (0.6.0). Should the Fire TV do the same, or keep a separate Home page? **Decision for you.**
- **Nuvio:** install the `nuvio-vista` fork on the Fire TV (same APK; minSdk 24) or the official `com.nuvio.tv` APK. There is no Play Store on Fire. Integration order from the brief:
  1. Intents and deep links: **available** (`nuvio://meta…`, launcher hand-off extras).
  2. Official API: **none public**.
  3. Web: not pursued.
  4. App launch: the fallback, already in place.
- **Settings (Phase 5)** extend Vista's DataStore settings: startup behavior, default destination, favourites and categories, theme and background (Immich or solid), Nuvio target package, Home-redirect troubleshooting (status, re-check, flash mode A/B), and version info.
- **No new permissions** beyond what Vista has. `INTERNET` exists today only for the optional Immich, Trakt, and weather features, and each stays off until configured. No analytics.
- **UI/UX:** I'll keep Vista's design language and apply 10-foot UI rules: large focus targets, a visible focus ring, safe-area margins, and a predictable Back stack. The UI/UX skills in this environment are game-focused, so I'll use them only for their gamepad-focus and safe-area guidance.

**Alternatives:**
- (2) Use only the Nuvio fork as the launcher. That brings GPL into everything and makes the app heavier.
- (3) A fresh codebase. That throws away working, already-tuned focus and launch code, so I don't recommend it.
- (4) Add a `firetv` product flavor inside `tvLauncher` itself. That's the least duplication, but this folder would go unused.

---

## 6. What is possible, uncertain, or impossible without root

**Confirmed possible (Reported on 8.1.8.x, to verify on 8.1.8.6):**
- Install and update our launcher over ADB; launch any installed app by intent; discover all apps, including sideloaded ones
- Redirect the Home button, boot, and wake to our launcher (with a flash unless option B is used)
- D-pad, Select, Back, and long-press handling inside our app
- Disable or restore individual Amazon packages with `pm disable-user` / `pm enable`
- Open Nuvio titles by deep link; launch Nuvio and return to our launcher with Back or Home

**Uncertain (needs device tests):**
- Whether role/set-home-activity (option D) has any effect on 8.1.8.6
- Whether the option B log trigger works on 8.1.8.6 and what it costs in idle CPU
- Whether option C (component disable) is safe on 8.1.8.6
- Continue Watching from the TV provider; notification listener; overlays
- The remote's extra buttons (Settings, app buttons, Recents) and whether they can be remapped
- Whether the accessibility service survives OTA updates on this model
- Performance on this stick: 2 GB RAM is more than the onn's, but this needs measuring

**Impossible without root:**
- Receiving the Home key itself, or stopping Amazon's launcher from ever starting
- Uninstalling the Amazon launcher, or disabling protected packages
- Reading the system-wide Watch Next aggregate; replacing Amazon's quick-settings overlay (long-press Home) or its sleep/screensaver UI
- Permanently blocking OTA updates without disabling update packages (possible but not advised)

### Debloat candidates (for later, each needs your approval)

Package names come from Fire OS 7 lists and **must be checked against `pm list packages` on this device**. The script will snapshot first, disable only with `pm disable-user`, and log a restore command for each package.

| Tier | Packages (examples) | Purpose | Side effects |
| --- | --- | --- | --- |
| Low risk | `com.amazon.hybridadidservice`, `com.amazon.tv.acr`, `com.amazon.wirelessmetrics.service`, `com.amazon.shoptv.client`, `com.amazon.storm.lightning.tutorial`, `com.amazon.tmm.tutorial`, `com.amazon.gamehub`, `com.amazon.minitv.android.app` | Ad ID, content recognition, metrics, shopping, tutorials, promos | Lose those Amazon features only |
| Medium | Alexa helpers (`com.amazon.tv.alexaalerts`, `…alexanotifications`, `com.amazon.alexa.externalmediaplayer.fireos`), Matter (`com.amazon.tv.matter*`), `com.amazon.tv.livetv` | Alexa alerts, smart home, Live tab | Some Alexa voice and smart home features break |
| Ask first | `com.amazon.tv.forcedotaupdater.v2`, `com.amazon.whisperjoin.middleware.np`, `com.amazon.tv.ffsprovisioneeclient` | Forced OTA, Wi-Fi setup for other devices, Frustration-Free Setup | Security updates, device setup |
| **Never** | `com.amazon.device.messaging`, `com.amazon.tv.launcher` (whole package), `com.amazon.firehomestarter`, `com.amazon.tv.oobe`, `com.amazon.tv.settings*`, Bluetooth, Wi-Fi, remote pairing, input, and `com.android.*` providers | Settings, setup, connectivity | Broken Settings menu, boot loop, lost remote |

---

## 7. Recommended implementation sequence

Each step ends with a device check and a doc update. Steps marked 🔒 change the device and need your go-ahead.

0. **Connect** (you): enable ADB on the Fire TV and give me its IP.
1. **Read-only inventory**: `getprop` (model and build), `pm list packages` (all and disabled), the current HOME resolution, accessibility settings, a TV-provider query, the remote's key codes via `getevent`, and RAM. Saved to `docs/DEVICE_INVENTORY.md`, plus a baseline snapshot for recovery.
2. **Toolchain**: `scripts/env.ps1` reusing the Unity JDK and a project-local SDK; `deploy.ps1` (build, `install -r`, launch).
3. **Import Vista** into this repo with history and build it unmodified. 🔒 Install it on the Fire TV as a normal app (no Home change).
4. **Fire compatibility**: the `Platform` layer, a blur fallback, Fire package aliases and settings intents, and a remote focus audit on the device.
5. 🔒 **Home activation (A)**: grant `WRITE_SECURE_SETTINGS`, enable the redirect, and test Home, boot, wake, app-exit, and screensaver. Try D once. Measure the flash.
6. 🔒 *(Optional)* **Option B**: grant `READ_LOGS`; measure the flash and idle CPU.
7. 🔒 **Nuvio**: install the fork or the official app; verify deep links, the hand-off, and Back/Home.
8. **Settings (Phase 5)**: startup, default destination, favourites/categories, theme/background, Nuvio target, troubleshooting, version.
9. **Docs**: setup guide, ADB guide, uninstall/recovery, limitations log.
10. 🔒 **Debloat script**: written separately, reviewed per package, run only with your explicit approval.
11. 🔒 *(Optional, high risk)* **Option C** experiment, only if A/B are not good enough.

---

## 8. Safe device recovery procedure

**Before any change**, `scripts/firetv/snapshot.ps1` saves these to `docs/firetv/device-snapshots/<date>.txt`:
- `pm list packages -d` (disabled packages)
- `settings get secure enabled_accessibility_services`
- `cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME`
- `dumpsys package com.amazon.tv.launcher` (component states)

**Recovery, from least to most drastic:**

1. **Turn off the Home redirect**: toggle it in launcher Settings. Over ADB, `settings put secure enabled_accessibility_services "<saved value>"` restores the snapshot value. Amazon home returns immediately, because nothing of Amazon's was changed.
2. **Uninstall the launcher**: `adb uninstall dev.drewett.vista`. This also removes its accessibility service and grants. Without ADB: hold **Home** to open Amazon's quick menu → Settings → Applications → Manage Installed Applications → Vista → Uninstall. *(Check during step 5 that the quick menu still opens with the redirect on.)*
3. **Undo debloat**: run the restore script, or `adb shell pm enable <pkg>` for each line in the debloat log. Re-enable a package **before** its components, because `pm enable <pkg>` resets component overrides.
4. **Undo option C** (if ever used): `adb shell pm enable com.amazon.firehomestarter`, then `adb shell pm enable com.amazon.tv.launcher/.ui.HomeActivity_vNext`. Confirm the exact component name from the snapshot first. Then reboot.
5. **ADB lost after an update**: re-enable Developer Options and ADB debugging; the IP may have changed.
6. **Last resort, a factory reset**: Settings → My Fire TV → Reset to Factory Defaults, or hold **Back + Right** on the remote for 10 seconds and confirm. This clears all `pm disable-user` states, grants, and sideloaded apps. You will need to sign in to Amazon again.

**Known gap:** if the device boot-loops *before* ADB or the UI is reachable, there is no root-free recovery except possibly the remote reset combo, and that may not register before the UI loads. This is why options C and broad debloating are last, opt-in, and done one package at a time with a reboot test after each.

---

## Decisions needed from you

1. **Codebase:** port Vista into this repo with history (recommended), or add a Fire TV flavor inside `tvLauncher`?
2. **Accessibility enablement:** may the launcher enable its own service after a one-time `WRITE_SECURE_SETTINGS` grant? Fire hides the toggle, so this differs from the onn rule.
3. **Home vs Nuvio:** should Nuvio be Home (as on the onn), or keep a separate Home page with Nuvio as a rail item?
4. **Nuvio target:** install the `nuvio-vista` fork, the official `com.nuvio.tv`, or both?
5. **OTA updates:** leave them alone (recommended)?

## Sources

- Home on Fire: https://github.com/toolicious/home-on-fire (GPL-3.0); XDA thread https://xdaforums.com/t/app-firetv-noroot-home-on-fire-use-any-launcher-as-your-fire-tv-home-home-button-boot-fire-os-8-7.4791567/
- FireStick Fixer: https://github.com/Hornet34/firestick-fixer (GPL-3.0)
- dkg-lt firetv-debloat: https://github.com/dkg-lt/firetv-debloat (MIT; Fire OS 7.7.1.3, 1st Gen 4K Max)
- XDA LTV Launcher guide for the 4K Max 2nd Gen (returned 403 to us, unread): https://xdaforums.com/t/guide-replace-the-fire-tv-home-launcher-with-ltv-launcher-on-fire-tv-stick-4k-max-2nd-gen-karat-no-accessibility-hijack.4803561/
- Boot-loop report: https://xdaforums.com/t/fire-os-8-stuck-on-black-firetv-logo.4634089/
- Fire OS 7 HOME behavior notes: https://github.com/aaronjothish/fire-tv-fireos7-notes, https://github.com/rlubsey/shelf/issues/1
- Platea Home issue (8.1.8.5, Home key never reaches the key filter): https://github.com/Gionnio/platea-home/issues/2
- Launcher Manager: https://xdaforums.com/t/app-firetv-noroot-launcher-manager-change-launcher-without-root.4176349/
- FTVLaunchX (Apache-2.0, Fire OS 7 and earlier): https://github.com/codefaktor/FTVLaunchX
- NuvioTV: https://github.com/NuvioMedia/NuvioTV (GPL-3.0); deep-link issue https://github.com/NuvioMedia/NuvioTV/issues/2235
- Local: `docs\INVESTIGATION.md`, `NUVIO_SHELL.md`, `NATIVE_NUVIO_OPTIONS.md`
