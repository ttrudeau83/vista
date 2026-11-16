package dev.drewett.vista.service.screensaver

import android.service.dreams.DreamService
import android.view.KeyEvent
import dev.drewett.vista.ui.photos.AmbientPhotoView

/** OS-managed idle dream. Manual ambient mode is a separate user-started activity. */
class ImmichDreamService : DreamService() {
    private var photos: AmbientPhotoView? = null
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true
        isScreenBright = false
        photos = AmbientPhotoView(this,allowMusic = false,exit = { finish() })
        setContentView(photos!!)
    }
    override fun onDreamingStarted() { super.onDreamingStarted(); photos?.start() }
    override fun onDreamingStopped() { photos?.stop(); super.onDreamingStopped() }
    override fun onDetachedFromWindow() { photos?.stop(); photos = null; super.onDetachedFromWindow() }
    override fun onDestroy() { photos?.stop(); super.onDestroy() }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = if (photos?.handleKey(event) == true) true else super.dispatchKeyEvent(event)
}
