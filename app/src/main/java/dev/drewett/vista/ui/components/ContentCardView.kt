package dev.drewett.vista.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import dev.drewett.vista.ui.home.panelFocus
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.drewett.vista.domain.ContentCard
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

private val CardShape = RoundedCornerShape(14.dp)

/** Content tile: thumbnail (framed by the row's focus ring) with the full title wrapping below. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContentCardView(
    card: ContentCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    flatFocus: Boolean = false,
    aspectRatio: Float = 16f / 9f,
    onLongClick: (() -> Unit)? = null,
    keepWholeCardVisible: Boolean = false,
) {
    val fullCard = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    // Surface's focus target includes only the image. Request the outer bounds after its
    // own focus scroll so the title and metadata below the image also stay in view.
    LaunchedEffect(focused, keepWholeCardVisible) {
        if (focused && keepWholeCardVisible) {
            withFrameNanos { }
            fullCard.bringIntoView()
        }
    }
    Column(modifier.panelFocus().bringIntoViewRequester(fullCard).onFocusChanged { focused = it.hasFocus }) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.focusPop(enabled = !flatFocus, shape = CardShape)
                .then(if (onLongClick != null) Modifier.appActivationKeys(onClick, onLongClick) else Modifier)
                .fillMaxWidth().aspectRatio(aspectRatio),
            shape = ClickableSurfaceDefaults.shape(CardShape),
            // flatFocus = no scale (the row slides under a pinned ring); both show the ring border.
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(if (flatFocus) 4.dp else 3.dp, MaterialTheme.colorScheme.primary), shape = CardShape),
            ),
            glow = ClickableSurfaceDefaults.glow(
                focusedGlow = Glow(MaterialTheme.colorScheme.primary, 12.dp),
            ),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (card.posterArtUri != null) {
                    AsyncImage(
                        model = card.posterArtUri,
                        contentDescription = card.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                // Provider badge (top-left).
                card.providerIcon?.let { icon ->
                    DrawableImage(
                        drawable = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(26.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                // Rating badge (top-right).
                card.ratingLabel?.let { rating ->
                    Text(
                        text = rating,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                card.progress?.let { p ->
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color.White.copy(alpha = 0.25f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(p.coerceIn(0f, 1f))
                                .height(4.dp)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }

        Text(
            text = card.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            minLines = if (flatFocus || keepWholeCardVisible) 2 else 1,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
        )
        val meta = card.episodeLabel ?: card.timeLeftLabel
        if (meta != null) {
            Text(
                text = meta,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                maxLines = 1,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}
