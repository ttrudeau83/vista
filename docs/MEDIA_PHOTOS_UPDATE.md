# Media and photo fixes

Power follow-up (`0.4.2-screensaver-power`): the Onn's remaining 20-minute
automatic sleep deadline was removed (`secure.sleep_timeout=-1`), while the
one-minute screen/dream timeout remains. The dream requests a bright screen;
the shared ambient photo view keeps the screen on only while active and clears
that request on stop. This also covers Photos + player inside the music screen.
Manual Power remains available. The previous sleep setting can be restored with
`adb shell settings put secure sleep_timeout 1200000`.

Installed version: `0.4.2-screensaver-power` (2026-11-16).

- Tabs select in place; stable focus targets and explicit horizontal navigation
  prevent duplicate moves. Content cannot steal focus when a tab mounts. Focused
  navigation uses the accent color; selected navigation stays neutral in content.
- SmartTube uses its published Subscriptions/Recommended/History rows. Reload
  refreshes sources and excludes disabled/uninstalled content publishers.
- App long-press uses an isolated dialog and release guard. Android force-stop
  remains a system App-info action, not a claimed third-party kill operation.
- Photo rotation survives failed requests. Twelve day-shuffled photos warm
  independently in the background; cached navigation never waits on originals.
  Originals upgrade preview quality in place. Cache remains bounded at 200 MiB.
- Photos and dreams support local hiding, thumbnail browsing and anniversary
  mode. Hidden photos are never archived/deleted on Immich. Manual ambient mode
  can keep external music playing; the dedicated player remains visible in its
  own Photos + player mode.
- Clock/weather stay in a corner; photos pan/zoom slowly. Weather attribution is
  removed from the slideshow overlay and credited here: Open-Meteo weather data,
  https://open-meteo.com/ (CC BY 4.0), with GeoNames-based geocoding.
- Family agenda fills available slots with events up to three weeks ahead.
  Theme text colors and nested settings Back navigation are corrected.

Verification: release compilation, vital lint and all 58 unit tests passed.
On-device diagnostics confirmed eight cached photos warming without manual Next,
an active Immich DreamService, and advancing image dimensions. Original image
access was verified with same-origin HEAD requests; no image bodies or screenshots
were copied to the host. Renton weather loaded successfully.

Google authorization is confirmed for `BlanktargetRecords`; the API returned
three playlists. Credentials/tokens stay encrypted on-device. The release removes
the debug-only provisioning provider. Supported music uses YouTube Data API and
its visible official player, not undocumented YouTube Music extraction.

Installed Nuvio publishes Continue Watching only. Trending cannot be honestly
claimed from that source until Nuvio publishes an additional catalog channel.
Screensaver selection is never enforced by the application; the one-minute
timeout is device configuration and the selected system dream remains user-owned.
