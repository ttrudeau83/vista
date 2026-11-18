# Nuvio shell fork (0.5.0)

User-approved on 2026-11-17: Option B of `NATIVE_NUVIO_OPTIONS.md`. Nuvio's
screens appear beside Vista's rail without moving Nuvio's code into Vista.

## How it fits together

- **Fork:** `../nuvio-vista` (sibling folder, its own Git repository), branch
  `vista-shell`, based on NuvioTV tag `1.1.0`. The upstream remote is named
  `upstream`. It is GPL-3.0; Vista remains MIT.
- **Package:** `dev.drewett.vista.nuvio`, signed with a personal key kept in the
  fork's ignored `nuviotv.jks`/`local.properties`. Official Nuvio APKs and its
  in-app updater do not update it.
- **Rail:** the fork replaces Nuvio's sidebar with a copy of `VistaNavigationRail`
  (`com.nuvio.tv.vista.VistaShell`). Nuvio's Home is the rail's Nuvio item;
  Nuvio's other sections (Library, and Discover when enabled) sit indented
  under it; Search and Settings at the bottom are Nuvio's.
- **Hand-off:** choosing a Vista item in the fork starts `dev.drewett.vista.MainActivity`
  with extra `dev.drewett.vista.extra.TAB` (a `VistaTab` name) and no window
  animation. Vista enters that tab as if chosen in its own rail. Unknown names are
  ignored. Vista's Nuvio item launches the fork the same way.
- **Search and profiles (0.5.1):** Search leads both rails and is Nuvio's search.
  Vista's Search and Nuvio items open the fork with extra
  `dev.drewett.vista.extra.NUVIO_ROUTE` = `search` or `profiles` (nothing else is
  accepted). `profiles` shows Nuvio's profile picker when 2+ profiles exist. If the
  fork is missing, Vista's own search and the regular Nuvio launch are used.
- **Nuvio is home (0.6.0):** the For you tab is gone; the Nuvio item carries the home
  icon. HOME, or a fresh Vista start without a requested tab, opens the fork with
  route `home` (pops back to Nuvio's home). Long-press Nuvio in either sidebar
  opens the profile picker. Movies/Shows titles open straight in the running fork
  (its launcher activity receives the `nuvio://meta` link); the details dialog
  appears only if that fails.
- **YouTube Kids:** under SmartTube in both sidebars; opens the official YouTube app
  (`com.google.android.youtube.tv`). YouTube offers no public way to preselect an
  account, so it opens on its own "Who's watching?" with the last-used (Ben's) first.
- **Preference:** Vista's Nuvio item, Continue Watching filter and Trakt title links
  prefer `dev.drewett.vista.nuvio`, then official `com.nuvio.tv`.

## Known limitations

- Nuvio's source omits its account server settings. For the user's own sign-in,
  the fork's ignored `local.properties` sets `NUVIO_SUPABASE_URL`
  (`https://api.nuvio.tv`), `NUVIO_SUPABASE_FALLBACK_URL`, `AVATAR_PUBLIC_BASE_URL`
  and the public `anon` key, read from the official app installed on the Onn.
  QR sign-in works with them (2026-11-17). Never commit these values; re-read them
  from the official APK if they change. TMDB and Nuvio's own Trakt client keys are
  still unset.
- Fork data is separate from official Nuvio; addons and settings are set up again.
- The fork's rail clock is 12-hour and does not read Vista's clock setting.

## Updating from upstream

In `../nuvio-vista`: `git fetch upstream --tags`, merge the next stable tag into
`vista-shell`, resolve conflicts (expected only in `MainActivity.kt`,
`app/build.gradle.kts` and `res/values/strings.xml`), build, and check playback,
sign-in, Back/Home and rail hand-off on the Onn before installing.

## Home redirect guard (0.6.3, 2026-11-17)

Home only reaches Vista/Nuvio while Vista's accessibility service is enabled;
otherwise the stock Google TV home appears. On 2026-11-17 Android itself cleared
`enabled_accessibility_services` (settings history shows `pkg:android`, no crash
logged; cause unknown). Vista never re-enables it over ADB — that is a security
setting the user turns on in Settings › System › Accessibility › Vista. Vista and
the Nuvio shell now check the setting on every resume and show a banner with an
"Open settings" button while it is off, so the failure is never silent.

Wireless ADB note: if the adb server restarts and mDNS finds nothing, connect to
the TV's address directly (e.g. `adb connect 192.168.0.82:45913`).
