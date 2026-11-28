package dev.drewett.vista.ui.home

import androidx.compose.foundation.Image
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.drewett.vista.R
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class VistaTab(val label: String,val icon:Int) {
    // Nuvio is home (2026-11-17): the former For you tab was removed.
    NUVIO("Nuvio",R.drawable.ic_nav_home),
    /** Rail action, not a page: opens Nuvio's "Who's watching?" profile picker. */
    PROFILES("Switch profiles",R.drawable.ic_nav_profiles),
    /** Trakt history only; Nuvio covers browsing, so these are kept off the rail (2026-11-27). */
    MOVIES("Movies",R.drawable.ic_nav_movies), SHOWS("Shows",R.drawable.ic_nav_shows), SMARTTUBE("SmartTube",R.drawable.ic_nav_tube),
    /** Opens the official YouTube app (Ben's account); SmartTube stays the parent's. */
    YOUTUBE_KIDS("YouTube Kids",R.drawable.ic_nav_kids),
    MUSIC("Music",R.drawable.ic_nav_music), PHOTOS("Photos",R.drawable.ic_nav_photos), FREEPLAY("Freeplay",R.drawable.ic_nav_live), APPS("Apps",R.drawable.ic_nav_apps),
}

@Composable
fun VistaTopBar(
    selected: VistaTab,
    onSelect: (VistaTab) -> Unit,
    onOpenQuickSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    use24Hour: Boolean = false,
    modifier: Modifier = Modifier,
    barFocusRequester: FocusRequester? = null,
    contentFocus: FocusRequester? = null,
    onActivate: (VistaTab) -> Unit = onSelect,
) {
    val colors = MaterialTheme.colorScheme
    val tabFocus = remember { List(VistaTab.entries.size) { FocusRequester() } }
    val searchFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    Box(modifier.fillMaxWidth().height(108.dp).background(Brush.verticalGradient(
        0f to colors.background.copy(alpha = 0.9f), 1f to Color.Transparent))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 22.dp).focusGroup()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && contentFocus != null) {
                        runCatching { contentFocus.requestFocus(); true }.getOrDefault(false)
                    } else false
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.height(48.dp)
                .then(if (barFocusRequester != null) Modifier.focusRequester(barFocusRequester) else Modifier)
                .focusProperties { enter = { tabFocus[selected.ordinal] } }.focusGroup()) {
                VistaTab.entries.forEach { tab ->
                    var focused by remember(tab) { mutableStateOf(false) }
                    val focusScale by animateFloatAsState(if (focused) 1.045f else 1f,
                        animationSpec = spring(dampingRatio = 0.78f, stiffness = 550f), label = "tabFocus")
                    Surface(
                        onClick = { onActivate(tab) },
                        modifier = Modifier.fillMaxHeight()
                            .graphicsLayer { scaleX = focusScale; scaleY = focusScale }
                            .focusRequester(tabFocus[tab.ordinal])
                            .focusProperties {
                                left = tabFocus[(tab.ordinal - 1).coerceAtLeast(0)]
                                right = if (tab.ordinal < VistaTab.entries.lastIndex) tabFocus[tab.ordinal + 1] else FocusRequester.Default
                            }
                            .onPreviewKeyEvent { event ->
                                if (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) {
                                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                                        val next = tab.ordinal + if (event.key == Key.DirectionRight) 1 else -1
                                        if (next in tabFocus.indices) tabFocus[next].requestFocus()
                                        else if (next == tabFocus.size) searchFocus.requestFocus()
                                    }
                                    true
                                } else false
                            }
                            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onSelect(tab) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.98f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (selected == tab) colors.onBackground.copy(alpha = 0.92f) else Color.Transparent,
                            contentColor = if (selected == tab) colors.background else colors.onBackground.copy(alpha = 0.65f),
                            focusedContainerColor = colors.primary,
                            focusedContentColor = colors.onPrimary,
                            pressedContainerColor = colors.onBackground,
                            pressedContentColor = colors.background,
                        ),
                    ) {
                        Row(Modifier.fillMaxHeight().padding(horizontal = 8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                            Image(painterResource(tab.icon),null,Modifier.size(16.dp),colorFilter=ColorFilter.tint(androidx.tv.material3.LocalContentColor.current))
                            Text(tab.label, style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (selected == tab) FontWeight.SemiBold else FontWeight.Medium)
                        }
                    }
                }
            }
            Box(Modifier.weight(1f))
            Clock(use24Hour)
            Box(Modifier.size(16.dp))
            BarIcon(R.drawable.ic_search, "Search", onOpenSearch, Modifier.focusRequester(searchFocus).focusProperties { left = tabFocus.last(); right = settingsFocus })
            Box(Modifier.size(12.dp))
            BarIcon(R.drawable.ic_settings, "Vista settings", onOpenQuickSettings, Modifier.focusRequester(settingsFocus).focusProperties { left = searchFocus; right = FocusRequester.Cancel })
        }
    }
}

@Composable
private fun BarIcon(icon: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val focusScale by animateFloatAsState(if (focused) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 550f), label = "toolbarFocus")
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, modifier = modifier.size(40.dp)
        .graphicsLayer { scaleX = focusScale; scaleY = focusScale }.onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.96f),
        colors = ClickableSurfaceDefaults.colors(containerColor = colors.onBackground.copy(alpha = 0.12f),
            focusedContainerColor = colors.primary)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Image(painterResource(icon), label, Modifier.size(22.dp),
                colorFilter = ColorFilter.tint(if (focused) colors.onPrimary else colors.onBackground))
        }
    }
}

@Composable
private fun Clock(use24Hour: Boolean) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(20_000) } }
    Text(SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm a", Locale.getDefault()).format(now),
        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
}
