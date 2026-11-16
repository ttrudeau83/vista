# Collapsible navigation and ambient controls (0.4.6)

The top menu is replaced by an 84 dp left rail. Its labels expand over the panel
to 224 dp while focused, then collapse when entering content. The content width
stays fixed, so row positions and artwork do not jump. Search and Settings live
in the rail; the clock sits at its foot.

Up/Down move between rail icons without switching the current panel or stopping
Music. Right or Select enters the chosen panel. Left from the first card/column
of a row opens the rail without scrolling to the top. Returning to the same panel
restores its focused item where possible; switching panels enters the new one.
Back in the rail returns to the panel. Nested Music views keep their own Back
history. Home/SmartTube rows, adaptive Apps/Movies/Shows grids, Photos buttons,
Music shelves and the player bar have explicit left-edge focus boundaries.

Music photo mode no longer displays the generic 'Music playing' caption. The
video is 224x126 dp at the bottom right, with title and controls above it.
Previous, play/pause and next are available alongside Show/Hide video and Return.
Title and controls fade to zero opacity after four idle seconds. The first D-pad
press reveals them; a faded Select is consumed before it can activate anything.
Hardware media buttons work directly and reveal the controls. The underlying
music player survives movement between Music browsing and photo mode.

Mixed ambient rotation gives an available unseen clip a turn after three still
photos, rather than burying it in a large photo pool. Shown/hidden exclusions
still apply. No ready clip means another unseen photo; unsupported media are
skipped without audio. Android's first-rendered-frame callback increments a
read-only counter for on-device validation.

MainActivity's standard dump adds only counts, media kind and controlled status
text under VistaDiagnostics. It emits no asset IDs, URLs, keys or media content.
This permits release diagnostics without repeatedly replacing the foreground
app with a debug APK. No additional exported component or permission is added.

Validation: release compilation, vital lint and all 87 unit tests, plus the user's confirmation
that the rail expands/collapses and Left returns from Photos. Live release
counts confirmed a cached clip. Live validation confirmed renderedVideoStarts=1, and the user saw a video play. No private media/screenshots were
copied from the TV to the host.
## 0.4.7 transition fix

The first rendered video frame now hides and clears the old ImageView before
fading the TextureView in. Letterbox regions show the renderer's black background,
not the previous photo. A failed video preparation still retains the previous
visual until another item is ready. Release compilation and vital lint passed;
the preceding rail/rotation suite passed all87 unit tests.
