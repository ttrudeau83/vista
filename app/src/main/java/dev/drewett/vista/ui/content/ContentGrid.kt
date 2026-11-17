package dev.drewett.vista.ui.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.drewett.vista.domain.ContentCard
import dev.drewett.vista.domain.dominantAspectRatio
import dev.drewett.vista.ui.components.ContentCardView
import dev.drewett.vista.ui.home.railBoundary
import kotlin.math.ceil

/** A titled grid of content tiles — reused by Movies, Shows and Search results. */
@Composable
fun ContentGrid(
    title: String,
    items: List<ContentCard>,
    onLaunch: (ContentCard) -> Unit,
    modifier: Modifier = Modifier,
    emptyMessage: String = "Nothing here yet",
    contentFocus: androidx.compose.ui.focus.FocusRequester? = null,
    onLongClick: ((ContentCard) -> Unit)? = null,
) {
    Column(modifier.fillMaxSize().padding(top = 28.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 48.dp, bottom = 8.dp),
        )
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
        } else {
            val aspect = items.dominantAspectRatio()
            val cellWidth = if (aspect < 1f) 168.dp else 240.dp
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val density = LocalDensity.current
            val labelHeight = with(density) {
                gridLineHeight(MaterialTheme.typography.titleMedium.lineHeight, MaterialTheme.typography.titleMedium.fontSize).toDp() * 2 +
                    (if(items.any { it.episodeLabel != null || it.timeLeftLabel != null })
                        gridLineHeight(MaterialTheme.typography.labelLarge.lineHeight, MaterialTheme.typography.labelLarge.fontSize).toDp() else 0.dp)
            } + 8.dp
            // Adaptive columns can stretch a portrait beyond the remaining viewport. Add
            // columns when needed so one whole card, its two title lines and scroll padding fit.
            val availableWidth = (maxWidth - 96.dp).coerceAtLeast(1.dp)
            val posterWidthLimit = ((maxHeight - 52.dp - labelHeight).coerceAtLeast(48.dp) * aspect)
            val regularColumns = ((availableWidth + 20.dp)/(cellWidth + 20.dp)).toInt().coerceAtLeast(1)
            val fittingColumns = ceil(((availableWidth + 20.dp)/(posterWidthLimit + 20.dp)).toDouble()).toInt().coerceAtLeast(1)
            val columns = maxOf(regularColumns, fittingColumns)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 12.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                itemsIndexed(items, key = { _, it -> it.stableKey }) { index, card ->
                    ContentCardView(
                        card = card,
                        onClick = { onLaunch(card) },
                        onLongClick = onLongClick?.let { action -> { action(card) } },
                        keepWholeCardVisible = true,
                        modifier = Modifier.fillMaxWidth()
                            .then(if(index%columns==0) Modifier.railBoundary() else Modifier)
                            .then(if (index == 0 && contentFocus != null) Modifier.focusRequester(contentFocus) else Modifier),
                        aspectRatio = aspect,
                    )
                }
            }
            }
        }
    }
}
