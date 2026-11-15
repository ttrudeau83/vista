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
- User target: Onn 4K running Google TV. Exact model, Android version, RAM,
  installed Nuvio variant/version, and TV-provider permissions remain unverified.
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

Record model and OS through ADB once paired. No device was configured or stock
launcher changed. First install/launch Vista as a regular app; validate launch,
Home/Back, app launching, permission-denied behavior and D-pad return focus before
optionally choosing it as Home. Record the stock Home component and recovery
procedure for that firmware before any Home-role change. Screensaver selection,
activation/exit, offline Immich behavior and Nuvio content resumes are future
device checks, not completed acceptance criteria.

## Baseline build result and software

The application source and Gradle configuration remain byte-for-byte identical
to Vista baseline. `:app:assembleRelease --no-daemon --stacktrace` was attempted
using Unity's bundled Temurin JDK 17.0.18, Android platform 34, and Gradle 8.7.
Gradle downloaded successfully and reached Android dependency setup, but the
build failed before compilation because **Android SDK Build-Tools 34.0.0 is
missing and its SDK license has not been accepted**. Unity has Build-Tools 36.0.0,
which does not satisfy this pinned baseline. No APK or successful startup is
claimed. Local diagnostic log: `.research/build-baseline.log` (ignored).

Existing tools are under:
`C:\Program Files\Unity\Hub\Editor\6000.6.0f1\Editor\Data\PlaybackEngines\AndroidPlayer`.
Its `OpenJDK`, `SDK`, `SDK/platform-tools` and `SDK/cmdline-tools/16.0/bin`
provide Java, Android platform 34, ADB and sdkmanager. A separate Android Studio
or Java installation is not required. Missing software is Build-Tools 34.0.0.

Once the SDK package/license is available, build in PowerShell with the actual
local paths (do not commit machine-specific SDK paths into application code):

```powershell
$androidTools = 'C:\Program Files\Unity\Hub\Editor\6000.6.0f1\Editor\Data\PlaybackEngines\AndroidPlayer'
$env:JAVA_HOME = "$androidTools\OpenJDK"
$env:ANDROID_HOME = "$androidTools\SDK"
$env:GRADLE_USER_HOME = "$PWD\.gradle-user"
.\gradlew.bat :app:assembleRelease --no-daemon --stacktrace
```

Expected APK: `app/build/outputs/apk/release/app-release.apk`. Upstream's release
variant uses debug signing; production distribution will need a private release
key. The upstream GitHub Actions workflow also builds this unmodified variant,
but no remote CI success is asserted by this investigation.

ADB could not start its local server within the restricted initial command;
no device connection or device-runtime checks were completed. No emulator is
configured. A connected Onn or a TV emulator is needed for runtime validation.
