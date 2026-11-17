# Trakt personal library

User-approved scope (2026-11-17): use Trakt as the source for the launcher's
Movies and Shows tabs, following the investigation of Nuvio's private Library.

The launcher connects directly to Trakt using its own API application and device
authorization. It does not reuse Nuvio credentials or inspect Nuvio private data.
The default source is the connected user's Watchlist; owned custom lists can be
selected in Trakt settings. The selected list is divided into movies and shows.
Long-press a title to open its list menu, then choose Remove to remove it from
the selected Watchlist or owned custom list on Trakt. The menu names the target
list; Cancel is initially focused and the opening hold cannot activate removal.
It does not add titles, mark watched, scrobble, or transfer saved items from Nuvio.

Register a personal Development app at https://app.trakt.tv/oauth/applications.
Enter its Client ID in Vista's Trakt settings, save, then select Connect Trakt.
Follow the verification URL and approve the displayed device code in a browser.
The optional legacy Client Secret field is only for an application whose Trakt
device flow requires it. No API client credentials are bundled in this repository.

Movies and Shows retain their existing published TV content until Trakt is
authorized. Once connected, the selected Trakt list becomes their source. Empty
lists are shown honestly. Continue Watching retains its TV-provider source.
Select a title to view its details and open that title in GitHub Nuvio 1.1.0+.
The public ACTION_VIEW `nuvio://meta` scheme is verified against the installed
1.1.0 manifest and upstream DeepLinkParserTest. Prefer IMDb ID, with TMDB ID as
fallback. No private extras or copied GPL code are used. If identifiers are
missing, the separately labeled home action opens Nuvio for manual searching.

## Privacy and storage

The integration contacts Trakt to authenticate and read the selected personal
list, owned list names and title metadata. It downloads and caches poster images
for those titles. Choosing Remove sends only that title's media type and Trakt ID
to the selected list's remove endpoint. A confirmed response updates the local
encrypted snapshot; failures retain the displayed list and report an error.
Client settings, tokens and cached list data stay on the TV;
there is no Vista backend or telemetry for this feature. Tokens and credentials
use Android Keystore encryption and storage excluded from backup. Disconnect
removes the local connection and cached Trakt library. App backup is disabled.
No Trakt password is requested or stored.

Vista uses Trakt but is not endorsed or certified by Trakt.

## References

- https://developer.trakt.tv/docs/create-an-app
- https://developer.trakt.tv/docs/authentication-oauth
- https://developer.trakt.tv/docs/images
- https://developer.trakt.tv/docs/api-use-policy

Live account authorization and list contents require the user's API application
and approval of the device code; host tests cannot substitute for that step.

Validation: the full release unit suite, release compilation/vital lint, and
debug/release assembly passed. APK signature verification passed. Release
`0.4.11-trakt` (version code 15) excludes the shell-only provisioning provider.
The user's existing Development app successfully generated a live device code
without a Client Secret; account approval and fetching the personal list are
separate checks.

Live verification completed on 2026-11-17: the TV received authorization and
loaded both movie and show watchlist entries, all their poster images, and three
owned custom lists. Release `0.4.11-trakt` was then installed with an in-place
update preserving app data. The temporary debug setup provider was removed by
the release installation. No API credentials or personal list contents were
written into source files.

Trakt enforces per-account community-app connection limits. If its authorization
page reports the limit has been reached, the user must choose an existing app
to disconnect or change their Trakt subscription before Vista can connect.
Do not revoke another integration automatically or reuse that app's credentials.

Maintenance validation (2026-11-17): `0.4.12-trakt-actions` (code 16) passed all
101 release unit tests, compilation, vital lint, APK assembly and signature
verification, then installed in place on the Onn. The installed Nuvio 1.1.0
resolved a public title VIEW intent to its MainActivity. Tests cover distinct
movie/show IDs, malformed link identifiers, removal request identity/list path,
and confirmed versus missing/malformed removal responses. No real user list
entries were removed during validation. The full-card scroll/long-press behavior
was reviewed in code; TV D-pad interaction was not automated.

Post-install regression: opening a populated Movies/Shows grid in 0.4.12 threw
`IllegalStateException: Only Sp can convert to Px` at ContentGrid's line-height
measurement. VistaTypography leaves both titleMedium and labelLarge lineHeight
unspecified. Compilation and previous data tests did not exercise this runtime
theme input. Version 0.4.13 resolves Unspecified and Em line heights into finite
Sp values before density conversion, retaining the full-card sizing behavior.
Regression tests use the actual VistaTypography and several TV font scales,
plus explicit/relative/missing font metrics.
