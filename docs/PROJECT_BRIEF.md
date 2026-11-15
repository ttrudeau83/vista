# Project: Custom Google TV Launcher

## Objective

Fork the open-source Vista Android TV launcher and develop a lightweight, visually polished, remote-first launcher inspired by Projectivy.

Primary integrations:
- Immich: family photos for home backgrounds and screensaver
- NuvioTV: Continue Watching and primary media app
- YouTube: app shortcut
- YouTube Music: app shortcut

Target hardware: Google TV 4K device. Exact model and Android version must be confirmed during setup.

## Repositories

- Launcher: https://github.com/kierandrewett/vista
- Nuvio: https://github.com/NuvioMedia/NuvioTV
- Immich TV reference: https://github.com/giejay/Immich-Android-TV
- Screensaver reference: https://github.com/theothernt/AerialViews

Use Vista as the foundation. Inspect licenses and dependencies before incorporating code from other repositories. Do not copy GPL code into the MIT project without addressing license compatibility.

## Architecture

Use Kotlin, Jetpack Compose for TV, coroutines, Flow, and DataStore.

Organize new functionality into independently testable components:

- `data/immich/`: authentication, album discovery, asset retrieval, caching
- `data/nuvio/`: TV channel discovery, watch-progress mapping, launch intents
- `data/apps/`: installed-app discovery and launching
- `ui/home/`: photo background, app shortcuts, Continue Watching
- `ui/settings/`: integration configuration and appearance preferences
- `service/screensaver/`: Android DreamService

Adapt to Vista's existing package structure instead of forcing unnecessary refactoring.

## Required features

### Immich

- Configurable server URL and API key
- Connection test
- Album selection
- Random and sequential photo rotation
- Configurable rotation interval
- Disk and memory caching
- Aspect-ratio-preserving crop or fit modes
- Dark gradient for readable overlays
- Offline fallback to cached photos
- Graceful handling of expired credentials and unavailable servers
- Never log or commit API keys

### Nuvio

- Detect installed NuvioTV
- Launch Nuvio from the home screen
- Investigate whether Nuvio publishes Android TV channels or Watch Next entries
- Display published Continue Watching metadata when available
- Preserve poster artwork, progress, and title
- Open the app-provided content intent rather than constructing undocumented deep links
- If Nuvio does not publish usable data, document the missing integration and propose a minimal Nuvio-side patch
- Do not access another app's private database or require root

### YouTube and YouTube Music

- Detect installed apps
- Display customizable shortcuts
- Launch using PackageManager-supported launch intents
- Gracefully handle missing or disabled apps

### Screensaver

- Implement a DreamService
- Reuse Immich photo-loading and caching components
- Allow album selection and configurable transitions
- Support optional clock overlay
- Avoid screen burn-in through appropriate overlay movement
- Test activation and exit on actual hardware
- Respect system screensaver settings and limitations

## UI requirements

- 16:9 television layout
- Full-screen Immich photo background
- Subtle gradient and translucent content surfaces
- Nuvio Continue Watching row
- Prominent Nuvio, YouTube, YouTube Music, and Photos shortcuts
- Fully functional D-pad navigation
- Predictable focus restoration
- No ads or unnecessary recommendations
- Smooth performance on constrained TV hardware

## Development rules

1. Inspect existing source code before modifying anything.
2. Keep the original launcher working throughout development.
3. Complete one milestone per task.
4. Prefer extending existing code over replacing it.
5. Do not introduce unnecessary dependencies.
6. Run compilation and relevant tests after changes.
7. Never assume undocumented Android or Nuvio APIs exist.
8. Keep device-specific setup outside application code.
9. Record architecture decisions and limitations.
10. Do not disable or uninstall the stock Google TV launcher automatically.

## First assignment

Do not implement the full product yet.

1. Clone and inspect Vista.
2. Identify its home-screen UI, application repository, TV-provider integration, settings storage, and launcher manifest.
3. Check Nuvio's current channel-publishing and content-intent capabilities.
4. Review Immich's current API requirements.
5. Identify the minimal changes required for a photo-background proof of concept.
6. Build the unmodified launcher.
7. Produce an implementation report with findings, risks, file paths, and the next small coding task.

Stop after this investigation and report. Do not begin implementing later milestones without approval.

## Acceptance criteria

- Launcher builds and starts successfully
- Home and Back behavior is correct
- Existing app launching still works
- Immich photos display without blocking UI interaction
- Nuvio launches reliably
- Continue Watching shows actual Nuvio data or an honest unavailable state
- Screensaver can be selected and tested where firmware permits
- No credentials are exposed
- Device remains recoverable to the stock launcher
