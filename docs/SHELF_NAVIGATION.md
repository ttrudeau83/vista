# Shelf navigation (0.4.9)

Home and SmartTube share a full-height scrolling list. The hero fills the space
above the first measured shelf, with 24 dp of breathing room below its captions.
Moving down scrolls the hero away and aligns the preceding shelf above the selected
shelf. Returning to the first shelf restores the hero. The shelf controller owns
vertical scrolling; each horizontal row retains its leading-edge focus alignment.
112-dp thumbnails and reserved two-line titles leave room for two content shelves
on a standard 540-dp-high TV viewport. Larger accessibility text may fit fewer rows.

The shared photo renderer puts the weather condition on its own line beneath the
location and temperature, including Music's photo cover and the system dream.

Nuvio appears directly after For You in the rail. Selecting it launches its installed
TV activity through the normal app repository. Vista's Nuvio panel also shows any
content Nuvio publishes through Android TV providers; an explicit Open Nuvio action
remains available after returning. Missing installations show an unavailable state.

Nuvio itself opens full-screen. This update does not overlay or embed another app.
Android cross-app activity embedding requires the embedded app's explicit opt-in
and compatible device support; the inspected Nuvio reference manifest does not opt
in. See [Android activity embedding](https://developer.android.com/develop/ui/views/layout/activity-embedding#cross-application-embedding).
