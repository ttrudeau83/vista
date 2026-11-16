# Eufy doorbell through Home Assistant

Design only, researched 2026-11-16. No Eufy login, installation or camera access
was performed. User requested a manual T8213 doorbell button and a small live
overlay for about 20 seconds after a physical ring.

## Recommended dependency

Use the user's existing Home Assistant host. The old `fuatakgun/eufy_security`
and `bropat/eufy-security-ws` projects point to the replacement ecosystem;
avoid starting a fresh installation on the archived WS stack. Install the
[eufy-sdk HACS integration](https://github.com/mega-yfue/ha-eufy-sdk) and its
[Supervisor bridge add-on](https://github.com/mega-yfue/ha-eufy-sdk-addon), or
the bridge Docker container for HA Container. The integration requires HA
2026.6.4 or later and the bridge includes go2rtc. Verify the user's HA version
and installation type first.

The legacy client's [device list](https://github.com/bropat/eufy-security-client/blob/master/docs/supported_devices.md)
explicitly lists T8213 (S330 Battery Doorbell Dual). The replacement SDK exposes
capabilities dynamically; device discovery must confirm live video and doorbell
events rather than assuming a model-specific path. The legacy T8213
[ring issue #934](https://github.com/bropat/eufy-security-client/issues/934)
was closed without a legacy fix, making an actual ring test essential.

## Authentication and hosting

Eufy credentials and session stay on HA's bridge. Its
[setup documentation](https://github.com/mega-yfue/ha-eufy-sdk-addon/blob/main/eufy_sdk_bridge/DOCS.md)
recommends a separate Eufy account with the shared home accepted, to avoid the
phone app displacing its session. Region must be US for this user; initial login
can require 2FA/captcha. HA OS runs the add-on persistently; no Windows Node.js
service is needed. Default bridge control port is 3000 and its RTSP port 8554;
neither needs public port forwarding.

Vista stores the HA URL and token in its encrypted vault, separate from Eufy
credentials. No token, event image, camera serial or stream URL enters Git or
diagnostic logs. HA credentials are not inherently camera-scoped: use a dedicated
non-admin HA user and verify its effective permissions.

## TV implementation contract

- Authenticate to HA's documented `/api/websocket` endpoint and subscribe to the
  configured doorbell's events. The new integration publishes `eufy_sdk_event`
  with semantic `doorbellPress`, plus a Ringing binary sensor. Prefer discrete
  events to avoid missing repeated presses while the sensor remains on. Learn
  actual event fields and entity IDs from the configured instance; never infer
  them from a friendly name. Reconnect with backoff and reject stale/replayed
  rings. [Event mapping](https://github.com/mega-yfue/ha-eufy-sdk/blob/main/custom_components/eufy_sdk/pushmap.py),
  [HA WebSocket API](https://developers.home-assistant.io/docs/api/websocket/).
- Manual Doorbell button opens live view; a ring opens a muted, non-focusable
  corner overlay across apps without pausing music/video or taking audio focus.
  Display 20 seconds after the first frame, with a separate bounded connection
  deadline. Close/disconnect on expiry and release the stream consumer. No
  continual camera preload or indefinite retry, especially on battery power.
- Video comes from the bridge's go2rtc restream, not an assumed native T8213 RTSP
  endpoint. The integration's
  [camera implementation](https://github.com/mega-yfue/ha-eufy-sdk/blob/main/custom_components/eufy_sdk/camera.py)
  supplies RTSP to HA/go2rtc and deliberately disables HA HLS workers on battery
  cameras to avoid repeated wakeups. Prefer an authenticated HA WebRTC session
  for Vista; select a direct LAN RTSP client only after checking access control
  and stream lifetime. Do not promise an HA HLS URL for this battery model.
- If live connection fails, show an honest unavailable message; an event still
  must be labelled as a still, not passed off as live video. No host screenshots
  or private image downloads are required for verification.

## Acceptance before enabling automatic overlays

Discover the T8213 camera and ring source; confirm manual video starts/stops;
press the real doorbell and observe a fresh event; confirm a 20-second overlay
above another app and that its existing audio continues; verify repeated presses,
HA disconnection/reconnect, dismiss, and no stream left open afterward. Battery
budget and actual first-frame latency remain hardware checks.

The replacement SDK is [Apache-2.0](https://github.com/mega-yfue/eufy-sdk/blob/main/LICENSE);
the legacy client is [MIT](https://github.com/bropat/eufy-security-client/blob/master/LICENSE).
Keep license/notice obligations if distributing bridge software. Vista need only
use HA APIs, so no Eufy protocol library needs to ship in its APK.
