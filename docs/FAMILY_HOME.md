# Family Home update

Version `0.3.0-family-home`, 2026-11-15.

## Delivered behavior

- Video/content rows stay at a fixed height. The hero owns the remaining clipped
  area, with wider titles capped at two lines instead of changing row position.
- Navigation is For you, Movies, Shows, SmartTube, Music, Photos, Apps. Library
  is removed from the bar. SmartTube/Music launch only on click, not merely focus.
  Installed targets on this Onn are `org.smarttube.stable` and
  `com.google.android.youtube.tvmusic`; PackageManager-resolved activities are used.
- Photos opens the selected Immich album viewer, with next-photo and settings
  controls. Home, Photos and the screensaver share the same private repository.
- The default Vista clock is 12-hour. Vista settings contains a persisted 12/24
  format toggle and a screensaver-clock toggle. Google TV settings is labeled
  separately. The remote Settings key opens Vista settings when its explicitly
  enabled accessibility service receives that key.
- The Family Home panel opens a read-only month grid and day agenda. Recurrence,
  all-day dates, cancellations, timezones, offline state and private subscription
  storage are described in [FAMILY_CALENDAR.md](FAMILY_CALENDAR.md).

The user supplied Immich credentials and a private calendar subscription for ADB
setup. These were provisioned into Android Keystore-encrypted storage on the Onn,
not embedded in APKs, source, documentation or Git. The only Immich album was
selected and a real photo successfully downloaded/validated. The live Family
feed parsed 37 occurrences in the supported date range. The user confirmed the
release Home screen shows the photo, 12-hour clock and calendar.

## Screensaver

The selected OS DreamService is
`dev.drewett.vista/.service.screensaver.ImmichDreamService`. Android controls when
it starts; Vista does not overlay or poll another app to force a dream.

Photos change every 30 seconds using bounded candidate selection without an
immediate repeat when alternatives exist. Cached photos can alternate offline.
Decode work runs on IO with a 1920x1080 bound. An optional clock changes position
every 30 seconds. The first physical remote input dismisses the noninteractive
dream normally. Unconfigured/unavailable photos with no cache cause it to finish.

AudioManager active-audio checks run before showing content and every second;
the dream exits if audio starts. Video playback also relies on the playback app
and Android honoring KEEP_SCREEN_ON. Silent video in an application that does
not correctly tell Android it is playing cannot be identified universally by an
ordinary third-party app. Actual SmartTube/Music idle-exclusion and dream
activation/exit remain hardware tests, not assumed guarantees.

With user authorization, device setup selected this dream and changed
`system.screen_off_timeout` from 600000 to 120000 milliseconds. Existing
`secure.sleep_timeout` remains 1200000 milliseconds (20 minutes), and dreams
remain enabled/activated on sleep. Changes are device configuration, not hardcoded
Onn settings in the app.

## Home-button behavior and recovery

The Onn gives Google TV's Home activity a higher resolution priority than
third-party launchers. Assigning Vista the Home role and preferred activity alone
did not change physical Home behavior: the previously enabled Projectivy
accessibility service still redirected Home to Projectivy.

The user explicitly authorized switching that redirection to Vista. Only the
Projectivy accessibility component was removed from the enabled-service list;
Vista's service was added and verified bound without crashes. No application was
disabled or uninstalled. Vista holds the Home role. The user then confirmed
physical Home opens Vista and its configured content.

Vista's service handles Home/Settings key events and a fallback window-state
event for the OS-resolved Home activity. It does not retrieve screen contents or
log remote keycodes. The fallback is limited to the resolved Home component,
not settings or another arbitrary app; short debounce prevents loops.

The original values are also saved locally in ignored
`.research/onn-settings-before-family-home.json`. Recovery commands below are
for this Onn, using an already connected ADB device; replace the existing
enabled-service list carefully if other services have subsequently been added.

```powershell
# Restore the prior Projectivy Home redirection.
adb shell settings put secure enabled_accessibility_services com.spocky.projengmenu/com.spocky.projengmenu.services.ProjectivyAccessibilityService
adb shell settings put secure accessibility_enabled 1

# Restore the original Google TV photo dream and 10-minute idle setting.
adb shell settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop
adb shell settings put system screen_off_timeout 600000
```

To return to stock Home rather than Projectivy, remove Vista/Projectivy Home
redirection from accessibility settings and choose Google TV in the Home picker.
Google TV remains installed and enabled throughout.

## Verification

- Release and temporary debug setup builds compiled; release vital lint passed.
- All 27 unit tests passed: 11 Immich protocol, 6 transport, 4 photo sequence,
  6 calendar parser cases.
- Release APK signature verified and installed on Onn Android 14/API 34.
- Installed release is non-debuggable and excludes the setup provider.
- Authenticated Immich photo retrieval and private Family feed parsing succeeded.
- User confirmed Home routing and photo/clock/calendar display on the release.
- Screensaver is selected with 120000 ms screen timeout, 20-minute sleep retained.
- Media exclusion and two-minute activation/exit await physical playback/idle tests.

The debug-only provisioning provider is protected by Android DUMP permission
and an explicit shell/root UID check. It is absent from release builds and
accepts credentials only through the user-authorized ADB setup. No private values
are included in its source. Installing the release afterward removes the endpoint
while preserving encrypted app data.
