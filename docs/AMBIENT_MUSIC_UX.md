# Music and ambient media UX (0.4.5)

Music now uses a fixed hero, landscape artwork shelves, circular channel/artist
cards, playlist/track views and a persistent bottom player within Music. It uses
actual Music recommendations, followed artists, library artists, or authenticated
YouTube subscriptions. Empty accounts get an honest empty state, never sample
channels. Channel cards browse Music or fall back to the channel's uploads.

D-pad Down from any hero action scrolls/focuses the first playlist. Down from the
playlist row scrolls/focuses the first channel; Up reverses that path. Repeated
key-down events are consumed. Back restores the prior browser location and row;
at root it returns through hero/top navigation instead of closing the launcher.

The playing queue is a separate snapshot: opening another playlist, refreshing
Music or viewing an artist doesn't change the current track or its next/previous
positions. The bottom bar offers previous, play/pause, next, queue and photos.
It preserves duplicate track positions and does not unexpectedly wrap. Native
media play/pause/next/previous keys work too. The embedded official player stays
in a stable hero preview and moves into photo mode without being recreated.

All top tabs now have original vector icons. Artwork, settings and navigation
retain a single subtle focus animation. Dark page backgrounds blend navy, violet
and teal; day mode retains a light variation. Music photo controls and title are
right-aligned, lose the black title backer and fade to 16% after four idle seconds.
The first Select while faded reveals controls before activating anything.

## Photos and videos

Photo mode settings independently control ambient framing (whole image / crop),
motion (off / subtle / gentle) and muted videos. Ambient defaults to whole image
and 2% subtle motion; zoom-out transitions finish at full framing. Home background
framing stays independent. Photos, manual slideshow and DreamService share the
same renderer. Videos are always muted, request no audio focus and show at most
one minute. Texture transforms preserve aspect ratio. Player/surface resources
are released when views stop; failed media are skipped.

A persistent viewed-ID history replaces the fixed repeating daily pool. Only
successful display marks an item shown. Ready unseen media are selected until
the eligible catalog is exhausted, then a new cycle can start. New-session entry
avoids the last seen item; explicit gallery selection can replay an item.
The rolling warm window targets up to 60 fresh photos and two fresh clips.
Viewed cache files retire after replacements exist, while current media retain
leases. Background warming stops near the budget to avoid eviction/redownload
loops with large clips, but can fetch a needed fresh replacement. Displayed photo
previews upgrade to full-size in place when available.

Limits: 10,000 catalog entries, 512 MiB / 120 media files, separate 30 MiB thumbnail
cache, 128 MiB per clip. Unsupported codecs, oversized clips or network errors
leave the current visual in place while other fresh media prepare. Offline
operation cannot promise new unseen content indefinitely. The current Music
queue loads the initial returned batch; continuation remains a future extension.

## Verification

86 release unit tests passed; debug and release APKs compile with vital lint.
Tests cover mixed catalog requests, video request bounds, unseen rotation,
rolling-window replacement, large-cache headroom, full-cycle detection, queue
boundaries and duplicate-safe positions, artist routing and actual channel art.
Live device checks use counts and decoder preparation only. No private photos,
videos or screenshots are exported to the host. Physical remote navigation and
visual framing require user validation on the TV.
On-device Music refresh confirmed five real channel subscriptions and a live
Music home feed. Catalog checks found 293 photos and 32 videos once paging
completed. Video endpoint HEAD checks returned HTTP200/video-mp4 at roughly3MiB
and10MiB; background cache/preparation probes timed out before playback. The
client now sends browser-compatible Range requests and defers decoder validation
to asynchronous playback. Foreground clip validation is pending a physical Home
press, respecting the user's no-window-activation instruction. No private media
was copied to the host.
