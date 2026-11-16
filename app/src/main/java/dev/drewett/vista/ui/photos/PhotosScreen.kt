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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import dev.drewett.vista.data.immich.ImmichRepository
import dev.drewett.vista.ui.home.ImmichBackgroundState
import dev.drewett.vista.ui.home.PhotoBackground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PhotosScreen(photo: ImmichBackgroundState, contentFocus: FocusRequester) {
    val context = LocalContext.current
    val repository = remember { ImmichRepository.get(context) }
    val config by repository.config.collectAsStateWithLifecycle()
    val current by repository.currentPhoto.collectAsStateWithLifecycle()
    val status by repository.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val file = current?.cachedFile ?: photo.file
    LaunchedEffect(config.namespace) {
        launch { try { repository.loadGallery() } catch (e: CancellationException) { throw e } catch (_: Exception) { } }
        while (true) {
            delay(20_000)
            if (!menu) try { repository.nextPhoto() } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        file?.let { PhotoBackground(it, photo.fit) }
        Column(Modifier.align(Alignment.BottomStart).padding(40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(config.albumName ?: "Immich Photos", color = Color.White)
            if (file == null) Text(status ?: "Preparing cached photos…", color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Button(onClick = { context.startActivity(android.content.Intent(context, dev.drewett.vista.PhotoGalleryActivity::class.java)) }, modifier = Modifier.focusRequester(contentFocus)) { Text("Browse photos") }
                Button(onClick = { scope.launch { try { repository.nextPhoto() } catch (e: CancellationException) { throw e } catch (_: Exception) { } } }) { Text("Next") }
                Button(onClick = { menu = true }, enabled = current != null) { Text("Photo options") }
                Button(onClick = { context.startActivity(android.content.Intent(context, dev.drewett.vista.PhotoSlideshowActivity::class.java)) }) { Text("Start screensaver") }
            }
        }
    }
    if (menu) androidx.compose.ui.window.Dialog(onDismissRequest = { menu = false }) {
        Column(Modifier.background(Color(0xFF152030)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Hide this photo only in Vista?", color = Color.White)
            Button(onClick = { menu = false; scope.launch { try { repository.hideCurrent(); repository.nextPhoto() } catch (e: CancellationException) { throw e } catch (_: Exception) { } } }) { Text("Hide and continue") }
            Button(onClick = { menu = false }) { Text("Keep photo") }
        }
    }
}
