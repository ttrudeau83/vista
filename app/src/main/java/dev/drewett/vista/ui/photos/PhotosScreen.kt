package dev.drewett.vista.ui.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import dev.drewett.vista.data.immich.ImmichRepository
import dev.drewett.vista.ui.home.ImmichBackgroundState

@Composable
fun PhotosScreen(photo: ImmichBackgroundState, contentFocus: FocusRequester) {
    val context = LocalContext.current
    val repository = remember { ImmichRepository.get(context) }
    val config by repository.config.collectAsStateWithLifecycle()
    val current by repository.currentAmbient.collectAsStateWithLifecycle()
    val view = remember { AmbientPhotoView(context,allowMusic = true,exit = {}) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(view,lifecycle) {
        val observer = LifecycleEventObserver { _,event ->
            if(event == Lifecycle.Event.ON_START) view.start()
            if(event == Lifecycle.Event.ON_STOP) view.stop()
        }
        lifecycle.addObserver(observer)
        if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) view.start()
        onDispose { lifecycle.removeObserver(observer);view.stop() }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { view },modifier = Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.BottomStart).padding(40.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(config.albumName ?: "Immich Photos",color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Button(onClick = { context.startActivity(android.content.Intent(context,dev.drewett.vista.PhotoGalleryActivity::class.java)) },modifier = Modifier.focusRequester(contentFocus)) { Text("Browse media") }
                Button(onClick = view::nextMedia) { Text("Next") }
                Button(onClick = view::showOptions,enabled = current != null) { Text("Media options") }
                Button(onClick = { context.startActivity(android.content.Intent(context,dev.drewett.vista.PhotoSlideshowActivity::class.java)) }) { Text("Start screensaver") }
            }
        }
    }
}
