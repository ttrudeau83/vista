# Dedicated Music frontend

Vista renders a native Home shelf browser, library playlists and track lists for
its authorized YouTube channel. It never fabricates profile identity or mixes.
Playback uses the YouTube IFrame player in Android WebView; leaving the activity
pauses it. Track completion advances to the next loaded playlist/mix item.

## Connections

Configure a Google OAuth client of type **TVs and Limited Input devices**, with
YouTube Data API v3 enabled and the account added as a test user when required.
Client credentials, tokens and pending device codes stay encrypted with Android
Keystore in backup-excluded storage. No Google password is requested.

**Connect account** requests `youtube.readonly` for owned playlists. Select the
intended channel during Google's device authorization. Vista displays the real
channel returned by Google rather than assuming the requested profile.

The existing connection is retained until a replacement token is accepted;
cancelling or expiry preserves playlists. A broader `youtube` authorization was
explicitly approved during testing, but Music still returned HTTP 400. Repeating
device sign-in does not resolve this. The separate browser connection below supplies Music authentication without
replacing the owned-playlist OAuth connection.
## Unofficial Music home

User approved use of the undocumented Music `browse` and `next` endpoints on
2026-11-16. Existing credentials are reused when accepted; the read-only token
returned HTTP 403 on the Onn; broader authorization returned HTTP 400. Web and
mobile client formats were checked. Current release retains the Web format.

The native Home screen renders actual returned categories, playlists and mixes,
with up to three pages of shelves, 30 cards per shelf and 200 loaded tracks.
Nested categories open further shelves; mixes/albums open track lists. Unsupported
renderers are skipped. Requests, responses and traversal depth are bounded.
Google can change or block this interface; failure retains the official library
with an honest status message. No third-party source or library was copied.
Protocol reference: https://github.com/sigma67/ytmusicapi (MIT).

## Photo screensaver with music

**Photo screensaver** retains the same player while displaying Immich photos.
**Hide video / Show video** covers or reveals the player, at the user's request.
Covered player descendants cannot receive focus or accessibility navigation.
This is a visual cover, not audio-only playback: video still streams, ads are not
filtered, and Google may restrict playback. The embedded player is not signed in
by the Data API OAuth token, so Premium benefits are not guaranteed. Covering a
player conflicts with Google's visible-player requirements; this should not be
represented as a compliant public YouTube API client implementation.

## Verification

Release tests and compilation are run before installation. Debug-only diagnostic
counts support checking authorization and shelf loading without exporting private
media. The diagnostic provider is absent from release APKs. Playback, UI motion,
Freeplay routing and physical D-pad behavior still need human remote validation.

References:
- https://developers.google.com/identity/protocols/oauth2/limited-input-device
- https://developers.google.com/youtube/v3/docs
- https://developers.google.com/youtube/iframe_api_reference
- https://developers.google.com/youtube/terms/developer-policies
Device OAuth alone remains unsuitable for Music home/mixes. Upstream issue:
https://github.com/sigma67/ytmusicapi/issues/921

## Browser connection (0.4.4)

Run `scripts/Connect-MusicBrowser.ps1` under PowerShell 7 with the temporary
diagnostic APK installed and the Onn paired over ADB. The helper listens only on
127.0.0.1 with a random port and one-time route, a 60-minute expiry, same-origin
POST enforcement, bounded input and no request-body logging or host session file.
The helper page explains copying signed-in Music Request headers. Never paste
those into chat, command arguments, tracked files or a public site.

Headers travel through ADB stdin to a private bounded import on the TV. The
shell-only debug provider consumes/deletes it; the helper also cleans up on
failure. The parser keeps only required session headers, replaces the captured
authorization with a fresh SAPISIDHASH for every request, and accepts only the
Music origin. Cookies never go to OAuth, Data API, images or arbitrary URLs.

The app verifies the active account name against the previously connected
channel and requires usable home shelves before atomically encrypting the new
session. Failed imports preserve the previous connection. The real browser
profile appears in Music and settings; disconnect removes it independently of
owned playlists. An expired OAuth token does not disable browser home access.

Finish by installing the release APK, which has no provisioning provider and
cannot be accessed with `run-as`. The existing session survives the upgrade.
Browser sign-out/session expiry can require repeating this one-time setup.
Backend authentication does not sign the embedded video player into Premium.

Validation: 72 release unit tests pass, including raw/JSON/wrapped header parsing,
hash vectors, injection/origin rejection, old vault compatibility, redaction and
profile checks. The loopback helper returns 400 for cross-origin input; dummy
same-origin input is rejected and its private TV import is removed. Live verification succeeded after the user's local submission: nine Music shelves,
93 cards and a real mix containing 24 playable tracks. The active browser profile
matched the previously authorized channel before encrypted persistence.
Mix routing also handles watchPlaylistEndpoint, VL playlist IDs, seed videos and
thumbnail overlays. Queue parsing skips unavailable tracks and video counterparts.
Only the initial returned queue batch is loaded; queue continuation is not yet
implemented. Live verification succeeded: nine shelves, 93 cards and 24 tracks in a returned mix.

## History, podcasts and layout (0.6.1, 2026-11-17)

Music home is a list of labelled rows: Recently played (`FEmusic_history`), the
first personal mix shelf, Podcasts (`FEmusic_library_non_music_audio_list`, only
`MPSP…` podcast pages), channels & artists, the remaining Music shelves, and a
footer with Refresh/Settings. Library shows playlists, podcasts, history and
artists. Podcast pages list episodes (`musicMultiRowListItemRenderer`, video ID
under `onTap.watchEndpoint`). Header actions (Play my mix, Library, History,
Open YouTube Music, Photos, settings) scroll sideways so they never clip beside
the live player. Up/Down moves row to row; Back restores the previous row.
"Open YouTube Music" launches `com.google.android.youtube.tvmusic` directly.

History and podcasts need a signed-in Music browser session. On 2026-11-17 the
stored session had expired (`account_menu` returned no profile; history replied
"Sign in to view your history"), so both rows were hidden and the screen now says
the sign-in expired. Re-run the browser connection (needs PowerShell 7 and the
diagnostic build) to restore them. Sending `onBehalfOfUser` with the brand page
ID was tried and rejected by Music, so it is not used. Logcat tag `VistaMusic`
records only counts, error types, renderer names and Music's own UI message.

## Keeping the Music sign-in alive (0.6.2, 2026-11-17)

The browser session was reconnected on 2026-11-17 (history 100 items, 7 podcasts,
`account_menu` signed in). To keep it from going stale, Vista now applies
YouTube Music's own `Set-Cookie` renewals to the stored session, as a browser
would: only cookies already in the session are updated; new names and deletions
are ignored; the import candidate is never modified; renewals are re-encrypted in
the vault. A keep-alive calls `account_menu` one minute after start and every six
hours while Vista runs, and shows the "sign-in expired" message if Google ended
the session. Late-loading rows no longer hide above the visible list.

Google can still end a copied session (sign-out in the source browser, "sign out
of all devices", password change, security checks). Chrome's device-bound session
cookies may age copied sessions faster. Copy from a Firefox private window when
possible, and close it without signing out. Reconnecting needs PowerShell 7
(installed via winget on 2026-11-17) and the diagnostic build, then the release.

## 0.6.4 focus fixes (2026-11-17)

- Up/Down move a whole row at a time and land on the card last focused in that row (first card on a
  first visit). The row and card are scrolled into view before focus moves, so focus is never left on a
  card that scrolled away (the earlier "stuck on Recently played" bug, caused by a row whose first card
  had been pushed off-screen when YouTube Music added cards in front of it).
- Back from a playlist, shelf page or track list returns to the exact card or track that opened it.

## 0.6.5 full-screen video (2026-11-17)

- The top-right video is a focusable tile (Right past the header buttons). Selecting it shows the same
  player full screen without restarting it; controls fade after 4 s and return on any key. Back or
  "Exit full screen" returns focus to the tile. The rail hides while full screen, as in the photo view.
