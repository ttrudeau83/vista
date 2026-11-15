# Immich photo-background preview

This milestone adds one selected album photo behind Vista's existing For You
screen. Slideshow scheduling and the DreamService remain separate milestones.
The user prefers SmartTube instead of YouTube; existing app discovery already
launches SmartTube, so no YouTube shortcut is being added.

## Configure on the TV

1. Open Vista and select its settings button (or Menu on a compatible remote).
2. Choose **Immich photos**.
3. Enter your HTTPS server address and a dedicated API key. Keys can be created
   in Immich's web user settings. The app accepts the origin or a URL ending in
   `/api`; it normalizes the API path.
4. Give the key `album.read`, `asset.read` and `asset.view` permissions. Some
   thumbnail-to-original fallbacks additionally need `asset.download`. Account
   login credentials are not used or stored by Vista.
5. Select **Test connection and load albums**, then choose an album. The
   connection test verifies album access; choosing an album verifies image
   access and primes its cache. Check the displayed status before leaving.
6. Select **Done** and return to **For you**. Use **Mode: Crop/Fit** to change
   framing, **Refresh photo** to refetch the first photo, or **Disconnect and
   clear saved photos** to remove integration data.

An empty key field keeps the saved key only when testing the same normalized
server. A different server requires a new key. HTTPS is required; this milestone
does not enable unencrypted LAN HTTP or accept invalid TLS certificates.

Public ping/version checks established compatibility work against the user's
server version 2.7.5; no account password or API key was needed for that check.
Authenticated end-to-end verification requires the key to be entered on the TV.

## Implementation and privacy

- `data/immich/ImmichProtocol.kt`: validated API URL, album and image parsing.
- `data/immich/ImmichClient.kt`: bounded network requests with timeouts and
  same-origin redirect checks. Credentials are sent as an `x-api-key` header,
  never a URL parameter. Transport exception bodies are not shown or logged.
- `data/immich/ImmichVault.kt`: AES-GCM Android Keystore encryption and an
  AtomicFile in `noBackupFilesDir`. The key and related connection settings
  share one encrypted atomic transaction to avoid mismatched account/config
  writes. This intentionally extends the existing DataStore-based theme/app
  preferences without putting secrets into plain Preferences DataStore.
- `data/immich/ImmichRepository.kt`: IO dispatcher, serialized mutations,
  account/album cache isolation, atomic last-good photo writes and disconnect.
- `ui/settings/`: D-pad-focusable controls, password-masked API-key input and
  safe connection/image error messages. The key is not saved in Activity state.
- `ui/home/ImmichBackgroundViewModel.kt` and `PhotoBackground.kt`: independent
  background state and Coil rendering, bounded to a 1920x1080 decode with crop
  or fit and dark scrims. The photo layer is not focusable and is not changed
  by focusing another app. White text/dark home colors are used over photos.
- `ImmichSettingsActivity.kt`: non-exported settings entry, accessible from
  both the main and standalone quick-settings panels.

Photos remain in app-private, backup-excluded storage. A previously cached photo
can display offline. Explicit refresh failure leaves the last good photo intact.
Switching account/album or disconnecting clears old integration photos. General
app backup is disabled as an additional guard against firmware backup behavior.
No secrets or private photo fixtures are committed.

The original hero background remains available when Immich is disconnected or
no photo is available. App discovery and launching are unchanged, and the stock
Google TV launcher remains installed and the default Home handler.

## Limits and verification

This is a single-photo proof of concept. It does not rotate photos, implement a
screensaver, redesign content rows or change Nuvio integration. Cache and JSON
response sizes are bounded; a server response beyond the limits produces an
honest error rather than unbounded allocation. An explicit photo refresh may
return the same first image. Decoder support depends on Android's image formats.

Protocol and local HTTP-server unit tests cover malformed responses, photo
filtering, credential-safe errors, URL boundaries and redirect isolation.
Android Keystore persistence, actual authenticated image retrieval, physical
D-pad text entry and offline restart need testing on the Onn. Build/install
results: `testReleaseUnitTest` and `assembleRelease` passed on 2026-11-15.
All 17 unit tests passed (11 protocol, 6 transport). Release signature verification
passed and version `0.2.0-immich-preview` installed successfully on the Onn.
The TV-listings grant was retained. No authenticated photo claim is made until
the user enters an API key and selects an album on the device.
