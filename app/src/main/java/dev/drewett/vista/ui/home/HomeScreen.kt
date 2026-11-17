package dev.drewett.vista.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.withFrameNanos
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import dev.drewett.vista.ui.components.VistaButton as Button
import androidx.tv.material3.Text
import dev.drewett.vista.ui.VistaSignals
import dev.drewett.vista.ui.apps.AppsScreen
import dev.drewett.vista.ui.components.AppContextMenu
import dev.drewett.vista.ui.content.ContentGrid
import dev.drewett.vista.ui.calendar.FamilyCalendarPanel
import dev.drewett.vista.ui.photos.PhotosScreen
import dev.drewett.vista.ui.content.SearchScreen
import dev.drewett.vista.ui.music.MusicScreen
import dev.drewett.vista.ui.theme.vistaBackdrop
import dev.drewett.vista.service.GoogleTvLaunchSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpenQuickSettings: () -> Unit = {},
    use24Hour: Boolean = false,
    viewModel: HomeViewModel = viewModel(),
    backgroundViewModel: ImmichBackgroundViewModel = viewModel(),
) {
    val photo by backgroundViewModel.photo.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val customGradient = dev.drewett.vista.ui.theme.LocalVistaAppearance.current.customGradient
    val darkColors = dev.drewett.vista.ui.theme.VistaDarkColors
    MaterialTheme(colorScheme = if (photo.file != null) darkColors.copy(
        primary = colors.primary, onPrimary = colors.onPrimary,
        background = if(customGradient) colors.background else darkColors.background,
        onBackground = if(customGradient) colors.onBackground else darkColors.onBackground,
    ) else colors) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.tv.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            HomeContent(onOpenQuickSettings, viewModel, photo, use24Hour)
        }
    }
}

@Composable
private fun HomeContent(onOpenQuickSettings: () -> Unit, viewModel: HomeViewModel, photo: ImmichBackgroundState, use24Hour: Boolean) {
    val context = LocalContext.current
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val smartTubeSections by viewModel.smartTubeSections.collectAsStateWithLifecycle()
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val favouritesSet by viewModel.favourites.collectAsStateWithLifecycle()
    val movies by viewModel.movies.collectAsStateWithLifecycle()
    val shows by viewModel.shows.collectAsStateWithLifecycle()
    val allContent by viewModel.allContent.collectAsStateWithLifecycle()
    val nuvioPackage = dev.drewett.vista.domain.PublishedContent.preferredNuvioPackage(apps.map { it.packageName })
    val nuvioApp = apps.firstOrNull { it.packageName == nuvioPackage }
    val smartTubeApp = apps.firstOrNull { dev.drewett.vista.domain.PublishedContent.isSmartTube(it.packageName) }

    var tab by remember { mutableStateOf(VistaTab.FOR_YOU) }
    var searchOpen by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<dev.drewett.vista.domain.AppEntry?>(null) }
    var musicAmbient by remember { mutableStateOf(false) }
    var freeplayError by remember { mutableStateOf<String?>(null) }
    var railFocused by remember { mutableStateOf(false) }
    var panelEntry by remember { mutableStateOf(0) }
    val topBarFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    val panelReturnFocus=remember { mutableStateOf<FocusRequester?>(null) }
    var pendingReturnFocus by remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(Unit) { runCatching { topBarFocus.requestFocus() } }
    LaunchedEffect(panelEntry) {
        if(panelEntry>0) {
            withFrameNanos { }
            runCatching { (pendingReturnFocus ?: contentFocus).requestFocus() }.onFailure { runCatching { contentFocus.requestFocus() } }
            pendingReturnFocus=null
        }
    }
    BackHandler(!railFocused && !musicAmbient && !searchOpen && menuApp==null) { runCatching { topBarFocus.requestFocus() } }
    val resetSignal by VistaSignals.reset.collectAsStateWithLifecycle()
    val refreshSignal by VistaSignals.refresh.collectAsStateWithLifecycle()
    val requestedTab by VistaSignals.requestedTab.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val freeplayIntent = remember(context) {
        Intent(Intent.ACTION_VIEW, Uri.parse("https://tv.google.com/freeplay/"))
            .setPackage("com.google.android.apps.tv.launcherx").addCategory(Intent.CATEGORY_BROWSABLE)
    }
    val freeplayAvailable = remember(loaded) { freeplayIntent.resolveActivity(context.packageManager) != null }
    fun openFreeplay() {
        if (!freeplayAvailable) return
        GoogleTvLaunchSession.beginFreeplay()
        runCatching { context.startActivity(freeplayIntent) }.onFailure {
            GoogleTvLaunchSession.finish()
            freeplayError = "Freeplay could not open. Check that Google TV is enabled and up to date."
        }
    }

    // The Nuvio shell's rail hands Vista items back here; behave as if chosen in our rail.
    LaunchedEffect(requestedTab) {
        val chosen = requestedTab?.let { name -> VistaTab.entries.firstOrNull { it.name == name } }
        VistaSignals.requestedTab.value = null
        if (chosen != null && chosen != VistaTab.NUVIO) {
            searchOpen = false; panelReturnFocus.value = null; pendingReturnFocus = null
            tab = chosen
            if (chosen == VistaTab.FREEPLAY) openFreeplay() else panelEntry++
        }
    }

    LaunchedEffect(refreshSignal) {
        viewModel.refresh()
        if (refreshSignal > 0) {
            suspend fun safely(block: suspend () -> Unit) {
                try { block() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
            }
            launch { safely { dev.drewett.vista.data.immich.ImmichRepository.get(context).loadGallery(refresh = true) } }
            launch { safely { dev.drewett.vista.data.calendar.FamilyCalendarRepository.get(context).refresh(force = true) } }
            launch { safely { dev.drewett.vista.data.weather.WeatherRepository.get(context).refresh(force = true) } }
            launch { safely { dev.drewett.vista.data.music.MusicRepository.get(context).refreshLibrary() } }
        }
    }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                GoogleTvLaunchSession.finish()
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(resetSignal) {
        if (resetSignal > 0) {
            tab = VistaTab.FOR_YOU
            searchOpen = false
        }
    }

    CompositionLocalProvider(LocalNavigationRail provides topBarFocus,LocalPanelReturnFocus provides panelReturnFocus) {
    Box(Modifier.fillMaxSize().vistaBackdrop()) {
        Box(Modifier.fillMaxSize().padding(start=if(musicAmbient) 0.dp else 84.dp)) {
        when (tab) {
            VistaTab.FOR_YOU ->
                if (!loaded) Loading() else if (sections.isEmpty()) EmptyContent("No launchable apps or published content.") else HomeTab(
                    sections = sections,
                    favourites = favouritesSet,
                    onLaunch = viewModel::launch,
                    onLaunchContent = viewModel::launchContent,
                    onAppLongPress = { menuApp = it },
                    topBarFocus = topBarFocus,
                    contentFocus = contentFocus,
                    resetSignal = resetSignal,
                    photo = photo,
                    calendarPanel = {
                        FamilyCalendarPanel(use24Hour = use24Hour,
                            onOpen = { context.startActivity(android.content.Intent(context, dev.drewett.vista.FamilyCalendarActivity::class.java)) })
                    },
                )

            VistaTab.NUVIO -> Column(Modifier.fillMaxSize().padding(top=28.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Nuvio", Modifier.padding(start=48.dp), style=MaterialTheme.typography.headlineMedium)
                Button(onClick={ nuvioApp?.let(viewModel::launch) }, enabled=nuvioApp!=null,
                    modifier=Modifier.padding(start=48.dp).focusRequester(contentFocus).railBoundary()) { Text(if(nuvioApp!=null) "Open Nuvio" else "Nuvio is not installed") }
                ContentGrid("From Nuvio", allContent.filter { it.packageName==nuvioApp?.packageName }, viewModel::launchContent,
                    modifier=Modifier.weight(1f), emptyMessage="Open Nuvio to browse its catalog.")
            }
            VistaTab.MOVIES -> TraktLibraryPanel(true, movies, viewModel::launchContent, contentFocus,
                nuvioApp != null, { nuvioApp?.let(viewModel::launch) })
            VistaTab.SHOWS -> TraktLibraryPanel(false, shows, viewModel::launchContent, contentFocus,
                nuvioApp != null, { nuvioApp?.let(viewModel::launch) })
            VistaTab.APPS -> if (!loaded) Loading() else AppsScreen(
                apps = apps,
                onLaunch = viewModel::launch,
                onLongPress = { menuApp = it },
                contentFocus = contentFocus,
            )
            VistaTab.PHOTOS -> PhotosScreen(photo = photo, contentFocus = contentFocus)
            VistaTab.MUSIC -> MusicScreen(contentFocus = contentFocus, onAmbientChanged = { musicAmbient = it },onRequestTopBar={ runCatching { topBarFocus.requestFocus() } })
            VistaTab.FREEPLAY -> Box(Modifier.fillMaxSize().padding(horizontal = 64.dp), contentAlignment = Alignment.CenterStart) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("Google TV Freeplay", style = MaterialTheme.typography.headlineLarge)
                    Text(freeplayError ?: if (freeplayAvailable) "Free live channels and more from Google TV."
                        else "Freeplay is unavailable on this device. Enable or update Google TV to use it.",
                        style = MaterialTheme.typography.bodyLarge)
                    if (freeplayAvailable) Button(onClick = ::openFreeplay, modifier = Modifier.focusRequester(contentFocus)) {
                        Text("Open Freeplay")
                    }
                }
            }
            VistaTab.SMARTTUBE -> if (!loaded) Loading() else HomeTab(
                sections = smartTubeSections,
                favourites = favouritesSet,
                onLaunch = viewModel::launch,
                onLaunchContent = viewModel::launchContent,
                onAppLongPress = { menuApp = it },
                topBarFocus = topBarFocus,
                contentFocus = contentFocus,
                resetSignal = resetSignal,
                photo = photo,
                heroAction = {
                    Button(onClick={ smartTubeApp?.let(viewModel::launch) }, enabled=smartTubeApp!=null,
                        modifier=Modifier.railBoundary().then(if(smartTubeSections.isEmpty()) Modifier.focusRequester(contentFocus) else Modifier)) {
                        Text(if(smartTubeApp!=null) "Open SmartTube" else "SmartTube is not installed")
                    }
                    if(smartTubeSections.isEmpty()) Text("Open SmartTube to configure its TV channels.", style=MaterialTheme.typography.bodyLarge)
                },
            )
        }

        }

        if (searchOpen) {
            Box(Modifier.fillMaxSize().padding(start=84.dp)) {
            SearchScreen(
                apps = apps,
                content = allContent,
                onLaunchApp = viewModel::launch,
                onLaunchContent = viewModel::launchContent,
                onDismiss = { searchOpen = false;runCatching { topBarFocus.requestFocus() } },
            )
        }

        }
        if(!musicAmbient) VistaNavigationRail(selected=tab,railFocus=topBarFocus,
            onEnter={ chosen ->
                pendingReturnFocus=if(chosen==tab) panelReturnFocus.value else null
                if(chosen!=tab) panelReturnFocus.value=null
                searchOpen=false;tab=chosen
                if(chosen==VistaTab.FREEPLAY) openFreeplay() else panelEntry++
                if(chosen==VistaTab.NUVIO) nuvioApp?.let(viewModel::launchSeamless)
            },onSearch={ searchOpen=true },onSettings=onOpenQuickSettings,
            onFocus={ railFocused=it },use24Hour=use24Hour,modifier=Modifier.align(Alignment.CenterStart))
        BackHandler(railFocused && !searchOpen && menuApp==null) { pendingReturnFocus=panelReturnFocus.value;panelEntry++ }
        AppContextMenu(
            app = menuApp,
            isFavourite = menuApp?.packageName in favouritesSet,
            onDismiss = { menuApp = null },
            onOpen = viewModel::launch,
            onToggleFavourite = viewModel::toggleFavourite,
            onAppInfo = viewModel::openAppInfo,
            onUninstall = viewModel::uninstall,
        )
    }
}
}

@Composable
private fun EmptyContent(message: String) {
    Box(Modifier.fillMaxSize().padding(horizontal = 64.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize()) {
        Text(
            text = "Loading…",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
