package dev.drewett.vista.ui.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import dev.drewett.vista.ImmichSettingsActivity
import dev.drewett.vista.data.immich.ImmichException
import dev.drewett.vista.data.immich.ImmichRepository
import dev.drewett.vista.ui.home.ImmichBackgroundState
import dev.drewett.vista.ui.home.PhotoBackground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Immich viewer for the selected photo album, with the same offline source as Home and dreams. */
@Composable
fun PhotosScreen(photo: ImmichBackgroundState, contentFocus: FocusRequester) {
    val context = LocalContext.current
    val repository = remember { ImmichRepository.get(context) }
    val config by repository.config.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        photo.file?.let { PhotoBackground(it, photo.fit) }
        Column(Modifier.align(Alignment.BottomStart).padding(40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(config.albumName ?: "Immich Photos", color = Color.White)
            if (photo.file == null) Text("Connect Immich and choose an album to display photos.", color = Color.White)
            message?.let { Text(it, color = Color.White) }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { context.startActivity(android.content.Intent(context, ImmichSettingsActivity::class.java)) }, modifier = Modifier.focusRequester(contentFocus)) { Text("Albums and settings") }
                Button(onClick = {
                    busy = true
                    scope.launch {
                        try { repository.nextPhoto(); message = null }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) { message = if (error is ImmichException) error.message else "Photo unavailable. Try again." }
                        finally { busy = false }
                    }
                }, enabled = !busy && config.albumId != null) { Text(if (busy) "Loading…" else "Next photo") }
            }
        }
    }
}
