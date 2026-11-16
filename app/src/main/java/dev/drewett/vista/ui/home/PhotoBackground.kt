package dev.drewett.vista.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

/** Non-focusable, bounded decode; no full-screen blur or focus-linked photo animation. */
@Composable
fun PhotoBackground(file: File, fit: Boolean) {
    val context = LocalContext.current
    val request = remember(file) {
        ImageRequest.Builder(context).data(file).size(3840, 2160)
            .memoryCacheKey(file.absolutePath).diskCachePolicy(coil.request.CachePolicy.DISABLED).build()
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(model = request, contentDescription = null, modifier = Modifier.fillMaxSize(),
            contentScale = if (fit) ContentScale.Fit else ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            listOf(Color.Black.copy(alpha = 0.68f), Color.Black.copy(alpha = 0.12f)))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.2f), 0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))))
    }
}
