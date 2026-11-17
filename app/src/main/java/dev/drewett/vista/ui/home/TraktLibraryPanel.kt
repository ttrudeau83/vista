package dev.drewett.vista.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.drewett.vista.TraktSettingsActivity
import dev.drewett.vista.data.trakt.TraktRepository
import dev.drewett.vista.domain.ContentCard
import dev.drewett.vista.domain.NuvioTitleLink
import dev.drewett.vista.ui.components.OpeningSelectGate
import dev.drewett.vista.ui.components.OpeningSelectHandoff
import dev.drewett.vista.ui.components.VistaButton as Button
import dev.drewett.vista.ui.content.ContentGrid
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/** Personal lists stay separate from provider-published resume cards. */
@Composable
fun TraktLibraryPanel(
    movies: Boolean,
    published: List<ContentCard>,
    onLaunchPublished: (ContentCard) -> Unit,
    contentFocus: FocusRequester,
    nuvioAvailable: Boolean,
    onOpenNuvio: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { TraktRepository.get(context) }
    val state by repository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var selected by remember { mutableStateOf<ContentCard?>(null) }
    var menuItem by remember { mutableStateOf<ContentCard?>(null) }
    var menuListId by remember { mutableStateOf<Long?>(null) }
    var menuListName by remember { mutableStateOf("Watchlist") }
    var removing by remember { mutableStateOf(false) }
    var menuError by remember { mutableStateOf<String?>(null) }
    var launchError by remember { mutableStateOf<String?>(null) }
    suspend fun refresh(force: Boolean = false) {
        try { repository.refreshLibrary(force) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Repository publishes connection status and retains its last list. */ }
    }
    LaunchedEffect(Unit) { refresh() }
    DisposableEffect(lifecycleOwner, repository) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) scope.launch { refresh() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val cards = remember(state.items, movies) {
        state.items.filter { it.type == if (movies) "movie" else "show" }.map { item ->
            ContentCard(
                id = item.traktId, title = item.title, description = item.overview,
                posterArtUri = item.posterUrl, intentUri = NuvioTitleLink.create(item.type, item.imdbId, item.tmdbId), packageName = null,
                providerIcon = null, seasonNumber = null, episodeNumber = null, episodeTitle = null,
                reviewRating = item.rating?.toString(), reviewRatingStyle = -1, releaseDate = item.year?.toString(),
                type = if (movies) 0 else 3, aspectRatioCode = 4, durationMillis = 0,
                positionMillis = 0, lastEngagement = 0, source = "trakt",
                internalProviderId = item.imdbId,
            )
        }
    }
    /** Opens the title in the Vista-shell fork (same 1.1.0+ link parser), then official GitHub Nuvio. */
    fun openTitleInNuvio(card: ContentCard): Boolean {
        val uri = card.intentUri ?: return false
        return listOf(dev.drewett.vista.domain.PublishedContent.NUVIO_SHELL_PACKAGE, "com.nuvio.tv").any { pkg ->
            runCatching {
                val info = context.packageManager.getPackageInfo(pkg, 0)
                check(info.longVersionCode >= 1077)
                val intent = if (pkg == dev.drewett.vista.domain.PublishedContent.NUVIO_SHELL_PACKAGE) {
                    // Hand the link to the shell's running launcher activity (singleTop) so it opens in
                    // the existing session instead of a second Nuvio screen with a new profile prompt.
                    checkNotNull(context.packageManager.getLeanbackLaunchIntentForPackage(pkg))
                        .setData(Uri.parse(uri))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                } else Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                    .setPackage(pkg).addCategory(Intent.CATEGORY_BROWSABLE)
                check(intent.resolveActivity(context.packageManager) != null)
                context.startActivity(intent, android.app.ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle())
            }.isSuccess
        }
    }
    /** Selecting a title opens it in Nuvio directly; the details dialog only appears if that fails. */
    fun onTitleSelected(card: ContentCard) {
        if (openTitleInNuvio(card)) return
        launchError = if (card.intentUri == null) null
            else "Could not open this title. The GitHub version of Nuvio 1.1.0 or newer is required."
        selected = card
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 48.dp, top = 24.dp, end = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = { context.startActivity(Intent(context, TraktSettingsActivity::class.java)) },
                modifier = Modifier.focusRequester(contentFocus).railBoundary()) {
                Text(if (state.authorized) "Trakt lists & settings" else "Connect Trakt")
            }
            if (state.authorized) Button(onClick = { scope.launch { refresh(force = true) } }, enabled = !state.loading) {
                Text(if (state.loading) "Refreshing…" else "Refresh")
            }
        }
        if (state.authorized) {
            Text(state.message ?: "From your Trakt library", Modifier.padding(start = 48.dp, top = 10.dp),
                style = MaterialTheme.typography.bodyMedium)
            val listName = state.lists.firstOrNull { it.id == state.selectedListId }?.name ?: "Watchlist"
            ContentGrid("${if (movies) "Movies" else "Shows"} · $listName", cards, ::onTitleSelected,
                modifier = Modifier.weight(1f),
                onLongClick = { menuItem = it; menuListId = state.selectedListId; menuListName = listName; menuError = null },
                emptyMessage = if (state.loading) "Loading your Trakt list…" else "No ${if (movies) "movies" else "shows"} in this Trakt list.")
        } else {
            ContentGrid(if (movies) "Movies" else "Shows", published, onLaunchPublished,
                modifier = Modifier.weight(1f), emptyMessage = "Connect Trakt to see your watchlist and lists here.")
        }
    }
    selected?.let { card ->
        val openFocus = remember { FocusRequester() }
        Dialog(onDismissRequest = { selected = null }) {
            Column(Modifier.width(520.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp)).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(card.title, style = MaterialTheme.typography.headlineSmall)
                card.releaseDate?.let { Text(it) }
                card.reviewRating?.let { Text("Trakt rating: $it / 10") }
                card.description?.takeIf { it.isNotBlank() }?.let { Text(it, maxLines = 5) }
                launchError?.let { Text(it) }
                if (card.intentUri == null) Text("This title has no supported ID for opening directly in Nuvio.")
                Button(onClick = {
                    if (card.intentUri == null) { selected = null; onOpenNuvio() }
                    else {
                        val opened = openTitleInNuvio(card)
                        if (opened) selected = null
                        else launchError = "Could not open this title. The GitHub version of Nuvio 1.1.0 or newer is required."
                    }
                }, enabled = nuvioAvailable,
                    modifier = if (nuvioAvailable) Modifier.focusRequester(openFocus) else Modifier) {
                    Text(if (!nuvioAvailable) "Nuvio is not installed" else if (card.intentUri == null) "Open Nuvio home" else "Open title in Nuvio")
                }
                Button(onClick = { selected = null }, modifier = if (!nuvioAvailable) Modifier.focusRequester(openFocus) else Modifier) { Text("Close") }
            }
            LaunchedEffect(Unit) { runCatching { openFocus.requestFocus() } }
        }
    }
    menuItem?.let { card ->
        val cancelFocus = remember { FocusRequester() }
        val opening = remember(card.stableKey) { OpeningSelectGate(OpeningSelectHandoff.take()) }
        fun closeMenu() { if (!removing) { menuItem = null; runCatching { contentFocus.requestFocus() } } }
        Dialog(onDismissRequest = ::closeMenu) {
            Column(Modifier.width(500.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .padding(28.dp).onPreviewKeyEvent { event ->
                    val key = event.nativeKeyEvent
                    opening.consume(key.keyCode, key.downTime, key.action == android.view.KeyEvent.ACTION_UP)
                }, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(card.title, style = MaterialTheme.typography.headlineSmall)
                Text("Remove from $menuListName? This also updates your list on Trakt.")
                menuError?.let { Text(it) }
                Button(onClick = { scope.launch {
                    removing = true; menuError = null
                    val priorStatus = repository.state.value.message
                    try {
                        repository.removeItem(if (card.isMovie) "movie" else "show", card.id, menuListId)
                        menuItem = null
                        runCatching { contentFocus.requestFocus() }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { menuError = repository.state.value.message?.takeIf { it != priorStatus }
                        ?: "Could not remove this title. Refresh your list and try again." }
                    finally { removing = false }
                } }, enabled = !removing && !state.loading) { Text(if (removing) "Removing…" else "Remove from $menuListName") }
                Button(onClick = ::closeMenu, enabled = !removing, modifier = Modifier.focusRequester(cancelFocus)) { Text("Cancel") }
            }
            LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
        }
    }
}
