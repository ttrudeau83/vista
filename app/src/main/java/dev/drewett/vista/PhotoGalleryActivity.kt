package dev.drewett.vista

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.drewett.vista.ui.photos.PhotoGalleryScreen
import dev.drewett.vista.ui.theme.VistaTheme

class PhotoGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VistaTheme(dark = true) { PhotoGalleryScreen(onDone = { finish() }) } }
    }
}
