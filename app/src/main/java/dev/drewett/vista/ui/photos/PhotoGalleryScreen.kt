package dev.drewett.vista.ui.photos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import coil.compose.AsyncImage
import dev.drewett.vista.data.immich.ImmichRepository
import dev.drewett.vista.data.immich.PhotoAsset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PhotoGalleryScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { ImmichRepository.get(context) }
    val gallery by repository.gallery.collectAsStateWithLifecycle()
    val hidden by repository.hiddenPhotos.collectAsStateWithLifecycle()
    val loading by repository.loading.collectAsStateWithLifecycle()
    val status by repository.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showHidden by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<PhotoAsset?>(null) }
    LaunchedEffect(Unit) { try { repository.loadGallery() } catch (e: CancellationException) { throw e } catch (_: Exception) { } }
    BackHandler(selected != null) { selected = null }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Immich photos", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Button(onClick = { showHidden = !showHidden }) { Text(if (showHidden) "Visible photos" else "Hidden (${hidden.size})") }
            Button(onClick = onDone) { Text("Back to settings") }
        }
        Text(if (loading) "Loading thumbnails…" else status ?: "Choose a photo to show or hide it only in Vista.", color = Color.White)
        LazyVerticalGrid(columns = GridCells.Fixed(4), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(if (showHidden) hidden else gallery, key = { it.id }) { asset ->
                Surface(onClick = { selected = asset }, colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF152030), focusedContainerColor = Color(0xFF263F61), contentColor = Color.White, focusedContentColor = Color.White)) {
                    Column {
                        val thumb by produceState<File?>(asset.cachedFile, asset.id) {
                            if (value == null) value = try { repository.thumbnail(asset.id) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                        }
                        AsyncImage(model = thumb, contentDescription = "Photo ${asset.date ?: ""}", modifier = Modifier.fillMaxWidth().aspectRatio(16f/10f).background(Color.DarkGray), contentScale = ContentScale.Crop)
                        Text(asset.date ?: "Photo", modifier = Modifier.padding(8.dp), color = Color.White)
                    }
                }
            }
        }
    }
    selected?.let { asset ->
        Dialog(onDismissRequest = { selected = null }) {
            Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(if (showHidden) "Hidden in Vista" else "Photo options", color = Color.White, style = MaterialTheme.typography.titleLarge)
                if (!showHidden) Button(onClick = {
                    selected = null
                    scope.launch { try { repository.showAsset(asset.id); context.startActivity(android.content.Intent(context, dev.drewett.vista.PhotoSlideshowActivity::class.java)) } catch (e: CancellationException) { throw e } catch (_: Exception) { } }
                }) { Text("View photo slideshow") }
                Button(onClick = {
                    selected = null
                    scope.launch { try { if (showHidden) repository.unhideAsset(asset.id) else repository.hideAsset(asset.id) } catch (e: CancellationException) { throw e } catch (_: Exception) { } }
                }) { Text(if (showHidden) "Show again in Vista" else "Hide only in Vista") }
                Button(onClick = { selected = null }) { Text("Cancel") }
            }
        }
    }
}
