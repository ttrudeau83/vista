# Remote buttons → TV inputs (0.7.0, 2026-11-17)

User-approved: the Onn remote's four app buttons (YouTube, Netflix, Disney+, Paramount+) switch the TV
to HDMI 1–4 instead of opening those apps.

## How it works

- **Keys:** the Onn remote layout (`Vendor_0957_Product_0031.kl`) sends `BUTTON_3`, `BUTTON_4`,
  `BUTTON_9` and `BUTTON_10` for those buttons. `VistaAccessibilityService` consumes them (down and up)
  and maps them through `RemoteInputButtons` (defaults in that order to HDMI 1–4; reassignable in
  Vista settings › TV inputs by pressing a button).
- **Switching:** the box cannot select another input over HDMI-CEC — Android reserves that to system apps
  (`HdmiControlManager` playback client is a system API). The TV is an LG (CEC vendor ID `00:E0:91`),
  so Vista uses LG webOS local network control (SSAP over WebSocket: `wss://<tv>:3001`, falling back to
  `ws://<tv>:3000`): `ssap://tv/switchInput` with `inputId = HDMI_n`. The TV is found by SSDP
  (`urn:lge-com:service:webos-second-screen:1`).
- **Pairing:** the first request shows an allow prompt on the TV; its client key is stored encrypted in
  `noBackupFilesDir/lgtv-v1` (Android Keystore AES-GCM). The registration manifest is LG's Connect SDK
  manifest (Apache-2.0) as used by aiowebostv; this is an undocumented-by-LG but widely used protocol.
- **Permissions (0.7.1):** a first pairing that requested only input permissions was refused
  `switchInput` ("insufficient permissions"), so Vista now requests aiowebostv's full permission set and
  re-pairs older keys (`PERMISSIONS_VERSION`). If `tv/switchInput` is still refused, it launches the TV's
  own input app (`system.launcher/launch`, `com.webos.app.hdmiN`).
- **TLS:** webOS uses a self-signed certificate, so certificate checks are skipped for that one
  connection, which is limited to a private-network (site-local) address.

## Limitations / to verify

- Requires LG's phone-control setting on the TV ("LG Connect Apps" / "Mobile TV On"; menu location
  varies by webOS version) and the TV on the same network. On 2026-11-17 neither the PC nor the box found
  the TV until the user turned on the TV's phone-control setting; pairing then worked.
- Confirmed with the physical remote: the app buttons reach Vista before Google TV launches the apps.
- Confirmed on this TV (2026-11-17): `tv/switchInput` does not exist ("Unknown method"), and the
  fallback `system.launcher/launch` with `com.webos.app.hdmiN` switches inputs.
- `adb shell input keyevent` events bypass accessibility key filtering (Google TV then launches the app
  as a global key), so remapping can only be tested with the physical remote.
- If the TV is off, nothing happens (no Wake-on-LAN).
