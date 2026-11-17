# Nuvio catalog integration

Investigated 2026-11-17 against upstream main commit
`7327eabf39c1481ad1e759e6b43aa14537009869`:
[Nuvio Android TV channel manager](https://github.com/NuvioMedia/NuvioTV/blob/7327eabf39c1481ad1e759e6b43aa14537009869/app/src/main/java/com/nuvio/tv/core/sync/androidtv/AndroidTvChannelManager.kt).

Nuvio still publishes one Android TV preview channel, Continue Watching. Its
Watch Next integration also publishes watch progress; neither exports the
in-app Trending catalogs. Projectivy can consume these same public TV channels,
but the inspected Nuvio source provides no Trending channel to either launcher.
This does not establish what an older Nuvio/Projectivy combination displayed.

Vista's Movies and Shows currently classify all readable TV-provider entries by
their explicit program type. They do not query Nuvio's internal catalogs. Their
behavior remains unchanged because filtering for a nonexistent Trending channel
would remove usable content without delivering the requested catalog.

The Nuvio navigation shortcut now prefers the official GitHub package
`com.nuvio.tv` when it coexists with Play Store's `com.nuvio.app`. If the GitHub
app is absent, the Play app remains usable. Published content continues to launch
through its original publisher's intent; installing another package does not
redirect old Play entries or transfer the Play app's settings/history.

Device installation verified on 2026-11-17: official GitHub stable `1.1.0`
(code 1077), package `com.nuvio.tv`, installed alongside preserved Play Store
`1.0.0` (code 1062), package `com.nuvio.app`. APK signature verification passed;
its SHA-256 matched the release asset digest
`0aee593e3cc5acba52a68a93b286966a4ccd9ff28dc58adce0d6743f2e19af06`.
The installed GitHub package resolves its TV launch activity through
`.launcher.AppIconDefault`.

## Smallest Nuvio-side bridge

Extend Nuvio's own Android TV sync to publish the selected movie/series Trending
catalogs as separate preview channels. Reuse the catalog results Nuvio already
loads for the active profile rather than accessing its private data from Vista.
Each channel should retain a stable ID incorporating profile, addon, catalog and
media type, distinct from the existing Continue Watching channel.

Publish title, synopsis, poster, explicit movie/series program type, catalog order,
stable content identity, and an app-created content-detail intent on each preview
program. Reconcile/remove stale programs on profile/catalog changes, retain cached
results on transient network failures, and keep Trending items out of Watch Next.
Vista can then consume these channels through its existing repository and select
them explicitly for Movies/Shows. This requires an upstream change or a custom
Nuvio build; installing an unmodified release alone does not add the bridge.

## Alternative: query the same addon

Nuvio's addon-backed catalogs are selected from addon manifests and fetched using
the addon base URL, catalog ID, media type and optional extra parameters. A catalog
named Trending is not a universal Nuvio endpoint. Vista could independently query
the same public addon if supplied its manifest/configuration URL and the desired
movie/series catalog IDs. The required values cannot be inferred from an app
installation without reading private configuration, which this project forbids.
Some addon URLs embed credentials and must remain encrypted on-device.

Follow-up on 2026-11-17: the installed 1.1.0 release's manifest, MainActivity and
DeepLinkParserTest explicitly support public ACTION_VIEW `nuvio://meta` title
links. Trakt cards now use this source-verified contract with IMDb ID, or TMDB
fallback, and restrict the destination to GitHub package `com.nuvio.tv` version
1077+. This supplies title navigation without private extras. It does not supply
Nuvio's catalog results or private Library. No Nuvio GPL source was copied into Vista.

Validation: release unit tests, release compilation, vital lint and release APK
assembly passed. Package preference tests cover both installation orders, Play
fallback, debug/fork fallback and no matching app. Device verification of the
updated shortcut is separate from these host checks.
