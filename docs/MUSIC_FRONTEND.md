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
device sign-in does not resolve this. Browser-session authentication is the next
required integration step and has not been provisioned.
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
Music home/mixes are implemented but unavailable with the current OAuth connection;
no claim of successful personalized shelf retrieval is made. Upstream issue:
https://github.com/sigma67/ytmusicapi/issues/921
