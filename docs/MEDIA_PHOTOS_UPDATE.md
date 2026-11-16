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

## 0.4.3: photo variety and menu polish

- Daily photo pool increased from 12 to 60. Originals warm up to 450 MiB;
  combined originals/previews are bounded to 512 MiB and 120 files. Thumbnails
  retain their separate 30 MiB limit. Actual count depends on album and file size.
- Ambient photos alternate subtle zoom in/out and horizontal/vertical pans over
  20 seconds. Pan distances stay within overscan; clock/weather remain static.
- Music screensaver has a reversible Hide video / Show video visual cover.
- Music Home renders unofficial service shelves/mixes, with library fallback.
  See MUSIC_FRONTEND.md for authorization requirements and limits.
- Freeplay appears before Apps. Select opens the Google TV deep link advertised
  by this Onn. Accessibility Home fallback permits the explicit Freeplay visit;
  a delivered Home key, returning to Vista, or opening another launchable app
  clears that exception. System UI/keyboard popups do not end the visit.
  If firmware consumes Home while Freeplay and stock Home share the same
  activity, window events alone cannot distinguish the two; this path still
  needs a physical-remote check on the Onn.
- Top navigation, settings tiles and app/content cards use one modest spring
  focus animation without changing layout positions or directional navigation.
- Eufy T8213 camera integration is paused at the user's request until their
  Home Assistant URL and HACS status are available. See EUFY_DESIGN.md.

Validation for this update: all 64 release unit tests passed. Debug and release
builds compiled; final release includes the Freeplay routing safeguard. Music
identity and three owned playlists were confirmed on the device; home shelves
remain blocked by Google's OAuth HTTP 400 response despite successful broader
authorization. Camera connection remains paused. No private photos or screenshots
were exported. Physical remote validation is still required for new UI behavior.