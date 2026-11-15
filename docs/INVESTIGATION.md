# Milestone 1: Vista foundation investigation

Investigation date: 2026-11-15. Scope: inspect upstream and integrations, attempt
an unmodified build, preserve the baseline, and propose one small next task.
No launcher features were implemented in this milestone.

## Repository and target

- Foundation: https://github.com/kierandrewett/vista
- Fork: https://github.com/ttrudeau83/vista
- Vista baseline: `d514d934573ddf31e07b8a1ed90ee6b22e1a04ca`.
- Development branch: `custom-google-tv`; `upstream` preserves the original,
  `origin` points to the user's fork. Original history and MIT license retained.
- Verified target: Onn 4K Streaming Box (`onn_4k_gtv`, device `YOC`), Android 14,
  API 34. RAM and full TV-provider data coverage remain unverified.
- Installed Nuvio: Play Store package `com.nuvio.app`, version `1.0.0`, code 1062.
  This is older than the current source inspected for integration capabilities.
- Original requirements: [PROJECT_BRIEF.md](PROJECT_BRIEF.md).

## Vista source map

Paths below are relative to the repository root; Kotlin sources use the existing
`app/src/main/java/dev/drewett/vista/` package tree.

| Area | Path | Findings |
| --- | --- | --- |
| Activity / Home reset | `MainActivity.kt` | Compose host; `onNewIntent` signals reset to For You/top. Home launch mode is singleTask. |
| Root / Back | `ui/VistaRoot.kt` | D-pad sound handling, Menu/Settings quick panel, Back closes quick settings. Broader Back/focus behavior needs hardware verification. |
| Home shell | `ui/home/HomeScreen.kt` | For You, Movies, Shows, Apps, Library; search and app context menu. Collects ViewModel flows. |
| Home layout / background | `ui/home/HomeTab.kt` | Full-screen background, focused-card hero, scrims, rows, focus/scroll behavior. `HeroBackground` currently blurs poster artwork and crossfades focus changes. |
| Home data | `ui/home/HomeViewModel.kt` | Repository composition and refresh on IO; sections flow; Continue Watching currently comes only from WatchNextPrograms. |
| Installed apps | `data/apps/AppRepository.kt` | Queries TV and standard launcher activities; TV entries win. Launches resolved components with MAIN and appropriate category. Existing discovery should find installed Nuvio/YouTube/music variants without inventing package intents. Launch failures are currently swallowed. |
| TV content | `data/channels/ChannelsRepository.kt` | Queries WatchNextPrograms and PreviewPrograms. Maps artwork, title, publisher, duration, position and app intent URI. Opens URI with `Intent.parseUri`; falls back to owning app through ViewModel. Query failures become empty lists. |
| Content model | `domain/ContentCard.kt` | Existing progress, artwork and intent fields can be reused. Channel/internal identity will need extending for preview fallback. |
| Preferences | `data/system/SettingsRepository.kt` | Preferences DataStore named settings; currently theme only. Separate favourites and launch-history DataStores already exist. |
| Settings UI | `ui/quicksettings/QuickSettingsPanel.kt`, `ui/home/SettingsViewModel.kt` | Current theme/system controls. Add integration configuration separately, rather than replacing these. |
| Launcher declarations | `app/src/main/AndroidManifest.xml` | HOME/DEFAULT/LEANBACK launcher; min SDK 28, target/compile SDK 34 in app Gradle config. Internet and READ_TV_LISTINGS already declared. No DreamService exists. |

All Kotlin paths in the table are under the package tree above, except the
explicit manifest path. There are no existing test source sets in this baseline.

## Dependencies and licenses

Vista is MIT. Keep its copyright and license notice in derivatives.
Existing libraries include Compose for TV, AndroidX TV provider, Coil 2.7.0,
coroutines 1.8.1, and Preferences DataStore 1.1.1. No new runtime library is needed
just to load authenticated photo bytes and render a background. Review transitive
license notices before eventual distribution; this is not a complete dependency
license audit.

The following repositories are reference-only; no source was incorporated:

- NuvioTV: GPL-3.0, https://github.com/NuvioMedia/NuvioTV/blob/main/LICENSE
- Immich Android TV: GPL-3.0, https://github.com/giejay/Immich-Android-TV/blob/main/LICENSE
- AerialViews: GPL-3.0, https://github.com/theothernt/AerialViews/blob/master/LICENSE

Implement independently against published APIs. Copying GPL implementation into
the intended MIT derivative would require revisiting distribution licensing.
Ignored `.research/` holds the Nuvio reference clone and API research, never APK input.

## Nuvio: source capability exists

Inspected Nuvio commit `e0e830b60147e128df744da987b184f47cf8e646`
(2026-11-15), source version `1.2.0-beta.1`. This is not proof of the installed
TV version's behavior. Paths below are relative to the Nuvio repository:

- `app/build.gradle.kts`: production application IDs include `com.nuvio.tv`
  and Play Store `com.nuvio.app`; debug variants differ. Detect installed
  launch activities through PackageManager.
- `app/src/main/java/com/nuvio/tv/core/sync/androidtv/AndroidTvChannelManager.kt`:
  creates a Continue Watching preview channel and reconciles PreviewPrograms;
  around lines 262-345 builds title, episode, poster, duration, position and
  explicit serialized `ACTION_VIEW` intents targeting Nuvio MainActivity.
- `app/src/main/java/com/nuvio/tv/core/recommendations/ProgramBuilder.kt`:
  builds/upserts WatchNextPrograms, including Continue type and the content URI.
- `app/src/main/java/com/nuvio/tv/NuvioApplication.kt`: starts channel sync.
  `core/sync/androidtv/AndroidTvChannelSyncService.kt` reconciles cached history;
  source also refreshes when Nuvio backgrounds and avoids constant foreground
  playback updates. Expect eventual rather than continuously live progress.
- `app/src/main/AndroidManifest.xml` and `MainActivity.kt`: exported activity
  receives the publisher's content intent, including new intents.

No Nuvio-side patch is currently indicated. Consume its published intent unchanged;
do not reconstruct its private extras, read its private database, or require root.
Some Nuvio progress is represented by synthetic duration/position: a percentage
is appropriate, but derived remaining minutes may be misleading.

Vista already preserves and launches content intents. Its Continue Watching only
uses WatchNext, however. A later Nuvio adapter should select the Nuvio-owned
Continue Watching preview channel as a fallback, retain channel identity,
deduplicate against readable WatchNext entries and preserve progress/artwork.
Do not treat every Nuvio preview program as Continue Watching.

Cross-app provider access depends on actual firmware permissions. Vista's README
and comments make permission claims which have not been validated on the Onn.
Do not assume READ_TV_LISTINGS alone makes all WatchNext data accessible. Denied
access, no history, missing Nuvio and unsupported publication need honest empty
or unavailable states, never fabricated content.

## Immich API requirements

Reviewed official OpenAPI for latest stable `v3.4.1` and development main
`e5d6eb7be67834f59d90e4b7a86f5dec7ac9af86`. The user's server version is still
unknown; choose and test a supported version before implementing the adapter.
Official API: https://api.immich.app/ ; stable schema:
https://github.com/immich-app/immich/blob/v3.4.1/open-api/immich-openapi-specs.json

Normalize the server origin/base path and append `/api` exactly once. Use an
`x-api-key` request header, never URL query credentials. Require HTTPS by default;
the current manifest does not opt into cleartext HTTP. Decide LAN HTTP support
explicitly later without enabling unrestricted cleartext by accident.

| Request relative to `/api` | Purpose / permission |
| --- | --- |
| `GET /server/ping`, `GET /server/version` | Reachability and version; public endpoints do not validate the key. |
| `GET /albums` | Discover accessible albums; `album.read`. |
| `GET /albums/{id}` | Album metadata; `album.read`. In stable 3.4.1 this response does **not** embed the asset list. |
| `GET /timeline/buckets?albumId=...` | Discover album time buckets; `asset.read`. |
| `GET /timeline/bucket?albumId=...&timeBucket=...` | Retrieve a bounded bucket; `asset.read`; use server schema/response shape, filter image assets. |
| `GET /assets/{id}/thumbnail?size=preview` | Background image; `asset.view`. Bound decode dimensions to screen size. |

Use album listing and an actual image fetch for the connection test. Thumbnail
requests can redirect to another size or the original, which requires
`asset.download`; handle permission failure honestly and do not forward secrets
across unrelated origins. Grant `asset.download` only if that fallback is needed.
Avoid adding `user.read` just for a connection test. Old search pagination and
album filters are deprecated in current metadata-search schema; do not assume
old client examples apply to this server version.

Store the API key using Android Keystore-backed encryption in app-private
storage, with backup excluded. Existing `allowBackup=true` must be addressed when
credentials are introduced. Put nonsecret preferences in DataStore. Never log
headers, keys, full request objects or error bodies containing secrets. Namespace
photo caches by server/account, clear private caches on disconnect/account switch,
and handle 401/403 separately from timeouts/offline errors. Coil's normal HTTP
cache alone is not a guarantee of offline availability: retain a bounded local
last-successful image and metadata explicitly. No credentials were requested or
used in this investigation.

## Decisions and next small coding task

Keep package `dev.drewett.vista`, the single app module, existing navigation,
app repositories and focus behavior for the proof of concept. A future rename
must consider install identity/signing and coexistence with upstream Vista.

After the baseline build is resolved, the next coding milestone should be one
authenticated Immich photo as the For You background, behind existing controls:

1. Add a small `data/immich/` client/repository with a version-aware API boundary,
   timeout/cancellation, typed loading/error states and injected transport.
2. Add a minimal TV-friendly settings form for URL, API key and album selection,
   with connection test and encrypted credential storage. Keep keys out of build
   config, committed fixtures and screenshots.
3. Add `ui/home/PhotoBackground.kt`; reuse Coil, explicit bounded local caching,
   crop/fit and dark gradients. Load asynchronously; keep the last good image
   on refresh failure and the original hero when the integration is disabled.
4. Connect a lifecycle-aware background state without coupling network requests
   to focus changes or delaying app discovery. Adjust the existing opaque
   `HomeTab` background so it does not cover the photo. Never make the photo
   focusable or restart row composition for image updates.
5. Add meaningful repository tests for authentication errors, malformed/empty
   albums, cancellation, cache isolation and offline last-good behavior. Verify
   D-pad/app launch responsiveness and baseline fallback on the device.

Do not add slideshow scheduling, screensaver, Nuvio adaptation or a home redesign
to that task. Later milestones can add rotation and then a DreamService sharing
the repository/cache, movable clock and system screensaver settings integration.

## Hardware validation still required

Wireless ADB pairing and connection succeeded on 2026-11-15. The baseline APK
installed successfully. It was opened through its normal leanback entry, and
read-only ADB diagnostics confirmed `dev.drewett.vista/.MainActivity` running as
the visible, resumed foreground activity. App-specific crash/provider log checks
returned no matching errors in the inspected window. This establishes process
startup, not visual correctness or complete runtime acceptance.

`READ_TV_LISTINGS` was granted for active user 0. No overlay, accessibility,
notification-listener, or secure-settings grants were added. The secondary user
was not configured. Provider data coverage and installed Nuvio 1.0.0 publication
must still be checked; newer upstream source is not proof of installed behavior.

The stock Home component remains
`com.google.android.apps.tv.launcherx/.home.HomeActivity`, verified before and
after installing/opening Vista. No Home-role change, stock launcher disabling,
uninstall, or automated remote input was performed. Physical remote validation
of D-pad, Back, Home returning to stock, app launching and return focus remains
pending. Screensaver activation/exit, offline Immich behavior and Nuvio content
resumes are future device checks, not completed acceptance criteria.

## Baseline build result and software

**Result: unmodified release APK built successfully on 2026-11-15.** The final
Gradle pass reported `BUILD SUCCESSFUL`, with 48 actionable tasks (18 executed,
30 up to date). Kotlin compilation and release vital lint passed. There are no
existing unit/instrumentation test source sets. Device process startup has since
been verified as described above; remote interaction checks remain pending.

The application source and Gradle configuration remain byte-for-byte identical
to Vista baseline. `:app:assembleRelease --no-daemon --stacktrace` was attempted
using Unity's bundled Temurin JDK 17.0.18, Android platform 34, and Gradle 8.7.
The initial build stopped because Build-Tools 34.0.0 and SDK license acceptance
were missing. With the user's approval, the SDK license was accepted and
Build-Tools 34.0.0 installed into ignored `.tools/android-sdk`. The existing
Android 34 platform was copied there from Unity. Gradle also installed
Platform-Tools 37.0.1. Unity's protected shared SDK could not be updated, so the
build uses the project-local SDK instead.

Windows denied Gradle 8.7 transform-cache directory renames during the build.
Single-worker mode and disabled filesystem watching did not prevent this.
Completed transform directories were moved to their expected cache names while
Gradle was stopped, followed by bounded retries; application and build source
were not edited. Related Gradle issue:
https://github.com/gradle/gradle/issues/28948 . Local diagnostic log:
`.research/build-baseline.log` (ignored).

The successful build required finishing several completed transform-cache
renames between stopped builds. This environment workaround may recur with
Gradle 8.7 on Windows; a future build-tool upgrade should be a separate task.
The earlier compiler warning was an upstream deprecated usage-stats constant;
native libraries were packaged without stripping, which was nonfatal.

Existing tools are under:
`C:\Program Files\Unity\Hub\Editor\6000.6.0f1\Editor\Data\PlaybackEngines\AndroidPlayer`.
Its `OpenJDK`, `SDK`, `SDK/platform-tools` and `SDK/cmdline-tools/16.0/bin`
provide Java, Android platform 34, ADB and sdkmanager. A separate Android Studio
or Java installation is not required. The required build tools are now installed
locally and remain outside Git.

Build in PowerShell with the actual
local paths (do not commit machine-specific SDK paths into application code):

```powershell
$androidTools = 'C:\Program Files\Unity\Hub\Editor\6000.6.0f1\Editor\Data\PlaybackEngines\AndroidPlayer'
$env:JAVA_HOME = "$androidTools\OpenJDK"
$env:ANDROID_HOME = Join-Path $PWD '.tools\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = "$PWD\.gradle-user"
.\gradlew.bat :app:assembleRelease --no-daemon --max-workers=1 --no-watch-fs --stacktrace
```

Built APK: `app/build/outputs/apk/release/app-release.apk` (18,620,241 bytes).
SHA-256: `d523cbec771a6157e9f8429a41b18780a6e0c688522284530a48a62051bbfcef`.
`apksigner verify --verbose` passed with APK Signature Scheme v2. `aapt dump
badging` confirms `dev.drewett.vista`, version `0.1.0`, min SDK 28, target SDK 34,
and leanback launch activity `dev.drewett.vista.MainActivity`. A Git comparison
against the Vista baseline confirmed no changes to app sources or Gradle files.
Upstream's release variant uses debug signing; production distribution will
need a private release key. No remote CI success is asserted by this investigation.

ADB runs successfully outside the restricted initial command. The Onn is paired
over Wi-Fi, and installation/foreground process startup were verified. No
emulator is configured. Pairing secrets and local network endpoints are excluded
from this report and application source.
