# Dedicated music frontend

Vista's Music tab is a native playlist and track browser for the authorized
YouTube channel, with a visible official YouTube player inside the app. It does
not launch YouTube TV as a substitute. No account identity, playlist, thumbnail
or playable stream is fabricated, and no undocumented YouTube Music API is used.

## One-time Google Cloud setup

1. In a Google Cloud project, enable **YouTube Data API v3**.
2. Configure the OAuth consent screen and add your Google account as a test user
   if the app is in testing mode.
3. Create an OAuth client with application type **TVs and Limited Input devices**.
4. Enter its client ID and client secret in Vista's **Music settings**. These are
   application credentials; your Google account password is never requested.
5. Choose **Connect account**. On your phone/computer, visit the exact Google
   verification URL shown on the TV and enter the code. Select the **Blanktarget
   Records** account/channel during Google's consent process.
6. Vista displays the channel returned by `channels.list(mine=true)`. Confirm
   the identity rather than assuming that a matching profile name was selected.
   If Google returns multiple authorized channels, select the intended channel.
   Some Google/Brand Account configurations authorize only a default channel;
   if the wrong channel is returned, change the default channel for third-party
   tools in YouTube settings and reconnect. Vista never relabels another channel
   as Blanktarget Records.

The device flow uses only `https://www.googleapis.com/auth/youtube.readonly`.
Client credentials and OAuth tokens are encrypted atomically with Android
Keystore in backup-excluded storage. They are not stored in public UI state,
logged, passed in Activity intents or committed to source. Pairing codes are
temporary and only displayed to the authorizing user. Disconnect removes local
credentials and attempts Google's token revocation endpoint; when offline,
remove the app's access in your Google Account if immediate remote revocation
is needed.

An unexpired device authorization is saved in the encrypted credential vault.
After process restart or installing an update, Vista resumes polling the same
code instead of silently creating a new one. Pending grant data is cleared on
success, denial, expiry, client replacement or disconnect. The private device
grant is never included in public UI state.

## Supported library and playback boundaries

The documented YouTube Data API exposes the selected channel's owned playlists
and their video items. Loading is bounded to 200 playlists and 1,000 items per
playlist to protect the TV's memory/API quota. It does not provide YouTube
Music's complete recommendations, purchases, subscriptions, uploads library or
audio-only catalog semantics. A YouTube Music brand/profile name alone does not
grant an API client access to those services.

Playback uses Android's OS WebView and the official IFrame API with the installed
app's package identity as the HTTPS referrer/base URL. Player controls and video
remain visible; playback pauses when this screen leaves the foreground. The app
does not extract audio/media URLs, suppress advertising, hide the video player
or play videos in the background. Private/restricted/embedding-disabled items
can be returned in library metadata but fail to play in the embedded player;
OAuth for the Data API does not sign the embedded player into YouTube.

While a track is open, **Photos + player** shows the Immich ambient photo view
in the same Music screen. The same embedded player moves to a visible 384×216 dp
corner without being recreated, paused or hidden. Its official controls and
advertising remain unobscured, with the song title alongside it. Back or
**Return to music** restores the normal player layout. Leaving the app still
pauses playback; this mode is foreground playback, not hidden background audio.

Google Cloud client credentials, real account authorization, Onn WebView player
compatibility and brand-channel selection require device validation. Until
client credentials are configured, the Music tab shows an honest connect/setup
screen rather than another app or fake library.

Primary references:
- https://developers.google.com/identity/protocols/oauth2/limited-input-device
- https://developers.google.com/youtube/data/reference/rest/v3/channels/list
- https://developers.google.com/youtube/data/reference/rest/v3/playlists/list
- https://developers.google.com/youtube/data/reference/rest/v3/playlistItems/list
- https://developers.google.com/youtube/iframe_api_reference
- https://developers.google.com/youtube/terms/required-minimum-functionality
