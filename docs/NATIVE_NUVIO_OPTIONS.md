# Native Nuvio integration options

The user asked on 2026-11-17 whether we could fork Nuvio and combine its media
experience with the launcher, while continuing to merge upstream updates.
This is feasible, but is a separate milestone from the current Trakt UI fixes.
No Nuvio code has been imported into Vista and no fork has been published.

## Recommended direction for a single native app

Use a NuvioTV fork as the application base and add the existing launcher's home
experience: installed-app shortcuts, Immich backgrounds/screensaver, calendar,
weather and remote navigation. Nuvio already owns its media catalog, accounts,
library, title details, sources and player, so those screens can navigate within
one process. Do not embed a second entire Android application inside Vista or
replace Nuvio's data layer with another independent implementation.

Keep launcher additions in a small separate package/module and add narrow entry
points to Nuvio's navigation. Preserve an upstream remote and track an explicit
stable release tag. Merge upstream changes into a separate integration branch,
resolve conflicts, run tests/builds and check playback, sign-in, Home/Back,
screensaver and D-pad behavior before updating the TV. Updates can be pulled,
but they are not automatically conflict-free or safe to install without checks.

NuvioTV is GPL-3.0; Vista retains its MIT notice. If distributing a combined
derivative, preserve notices and comply with GPL source/license requirements
for the combined work. MIT code may be incorporated while retaining its notice;
Nuvio code cannot simply be relabeled as MIT. Review bundled native components
and dependencies as part of the fork milestone. Use a distinct package identity
and our own signing key; official Nuvio APKs would not update that fork. Keep SDK
installs, signing keys and account secrets outside source control. Plan explicit
account/setup migration rather than reading the official app's private data.

## Smaller alternative

Keep the lightweight launcher and maintain a Nuvio fork with a public channel
bridge for library/catalog metadata and app-created content intents. This keeps
playback separate and reduces launcher memory/dependency impact, but it remains
two applications. The existing public title VIEW scheme solves title opening
without needing a fork; it does not export the full Nuvio library.

## Proposed first fork milestone

Inspect current Nuvio dependencies/licensing and build the unmodified stable tag,
then add only a launcher Home entry and app-shortcut screen in an isolated fork.
Measure idle memory/startup on the Onn and verify stock-launcher recovery before
porting Immich/calendar/screensaver or changing any existing device installation.

References:
- https://github.com/NuvioMedia/NuvioTV
- https://github.com/NuvioMedia/NuvioTV/blob/1.1.0/LICENSE
- https://github.com/NuvioMedia/NuvioTV/blob/1.1.0/app/src/main/AndroidManifest.xml
- https://github.com/NuvioMedia/NuvioTV/blob/1.1.0/app/src/test/java/com/nuvio/tv/core/deeplink/DeepLinkParserTest.kt
