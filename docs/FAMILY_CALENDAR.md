# Family calendar

The home panel opens a read-only month view with a day agenda. Configure it with
the Family calendar's private iCal subscription URL in Google Calendar settings,
under **Family → Integrate calendar → Secret address in iCal format**. Enter this
link in Vista's calendar settings; do not make the calendar public. Google account
passwords and a Codex plugin connection are not credentials for the Android app.

The private URL and cached feed are encrypted together with Android Keystore
AES-GCM in an atomic file under `noBackupFilesDir`. Public repository state never
contains the feed URL. The settings screen masks the URL, excludes it from saved
Activity state and uses `FLAG_SECURE`. Disconnect removes the cached subscription.
No private feed URL or event fixtures are committed.

Calendar requests use HTTPS, same-origin redirects, bounded connection/read
timeouts and a 2 MiB feed limit. Refresh occurs when the panel/calendar is active,
at most every 15 minutes unless manually requested. An unavailable server leaves
the last saved events visible with an offline label. No background polling,
remote calendar writes, account scopes or public sharing are needed.

The parser uses biweekly 0.6.8 and its required vinnie dependency. Their BSD/MIT
notices and embedded recurrence/codec notices are packaged in `assets/licenses`.
Optional Jackson JSON support is unnecessary for the plain iCal feed.

Recurrence rules, exclusions, timezones, all-day dates, canceled events and
individual moved/canceled occurrences are supported. Month browsing covers the
previous month through six months ahead. Event limits bound parsing work;
unsupported `RANGE=THISANDFUTURE` overrides fail rather than showing fabricated
occurrences. Rare RFC5545 period-valued RDATE entries are a biweekly limitation.
An unsupported or over-limit feed preserves the previous successful cache.

Tests cover weekly recurrence/EXDATE, canceled and moved occurrences, all-day
exclusive end dates, midnight-spanning events, malformed feeds and canceled
recurrence masters. On-device private-feed authentication and date display still
were subsequently checked using the user-authorized ADB setup: the private feed
loaded and parsed 37 occurrences, and the user confirmed its Home panel appears.
The masked TV form remains available for future changes without ADB. No private
feed URL is included in this repository.
