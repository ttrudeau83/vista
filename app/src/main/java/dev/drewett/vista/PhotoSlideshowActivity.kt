package dev.drewett.vista

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import dev.drewett.vista.ui.photos.AmbientPhotoView

/** User-started photos, including while another application continues playing music. */
class PhotoSlideshowActivity : ComponentActivity() {
    private lateinit var photos: AmbientPhotoView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = 5894
        photos = AmbientPhotoView(this,allowMusic = true,exit = { finish() })
        setContentView(photos)
    }
    override fun onStart() { super.onStart(); photos.start() }
    override fun onStop() { photos.stop(); super.onStop() }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = if (photos.handleKey(event)) true else super.dispatchKeyEvent(event)
}
