# Appearance controls (0.4.8)

Vista settings → Appearance controls the focus outline color and the two endpoints
of the page background gradient. Quick swatches work with the remote; a six-digit
RGB field accepts any custom color. Changes persist immediately in the existing
settings DataStore. Reset appearance restores only these colors and the original
adaptive background, leaving accounts, clock and photo preferences unchanged.

Cards, playlist tracks, channels and buttons draw a three-dp outline in addition
to their existing focus animation. Circular cards keep circular outlines. The
accent is preserved when the home screen switches to its photo-backed dark theme.
Custom gradients replace the default gradient and vignette; content text adapts
to the average background brightness. Photo backgrounds and screensavers keep
their media unchanged.

Music's Photos action now follows Browse library in the header, including playlist
and queue views. During playback it opens the photo cover without replacing the
queue; before playback it opens the ordinary slideshow. The bottom player retains
previous, play/pause, next and queue controls.
