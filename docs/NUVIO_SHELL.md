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
- **Preference:** Vista's Nuvio item, Continue Watching filter and Trakt title links
  prefer `dev.drewett.vista.nuvio`, then official `com.nuvio.tv`.

## Known limitations

- Nuvio's official account server is not available to unofficial builds (Nuvio
  ships its Supabase, TMDB and Trakt client keys only in official APKs). Without
  them the fork works with locally added addons; Nuvio-account sync, TMDB
  enrichment and Nuvio's own Trakt connection need keys supplied in the fork's
  ignored `local.properties`.
- Fork data is separate from official Nuvio; addons and settings are set up again.
- The fork's rail clock is 12-hour and does not read Vista's clock setting.

## Updating from upstream

In `../nuvio-vista`: `git fetch upstream --tags`, merge the next stable tag into
`vista-shell`, resolve conflicts (expected only in `MainActivity.kt`,
`app/build.gradle.kts` and `res/values/strings.xml`), build, and check playback,
sign-in, Back/Home and rail hand-off on the Onn before installing.
