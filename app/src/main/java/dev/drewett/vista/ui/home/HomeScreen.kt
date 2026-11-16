package dev.drewett.vista.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.drewett.vista.ui.VistaSignals
import dev.drewett.vista.ui.apps.AppsScreen
import dev.drewett.vista.ui.components.AppContextMenu
import dev.drewett.vista.ui.content.ContentGrid
import dev.drewett.vista.ui.calendar.FamilyCalendarPanel
import dev.drewett.vista.ui.photos.PhotosScreen
import dev.drewett.vista.ui.content.SearchScreen

@Composable
fun HomeScreen(
    onOpenQuickSettings: () -> Unit = {},
    use24Hour: Boolean = false,
    viewModel: HomeViewModel = viewModel(),
    backgroundViewModel: ImmichBackgroundViewModel = viewModel(),
) {
    val photo by backgroundViewModel.photo.collectAsStateWithLifecycle()
    MaterialTheme(colorScheme = if (photo.file != null) dev.drewett.vista.ui.theme.VistaDarkColors else MaterialTheme.colorScheme) {
        HomeContent(onOpenQuickSettings, viewModel, photo, use24Hour)
    }
}

@Composable
private fun HomeContent(onOpenQuickSettings: () -> Unit, viewModel: HomeViewModel, photo: ImmichBackgroundState, use24Hour: Boolean) {
    val context = LocalContext.current
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val favouritesSet by viewModel.favourites.collectAsStateWithLifecycle()
    val movies by viewModel.movies.collectAsStateWithLifecycle()
    val shows by viewModel.shows.collectAsStateWithLifecycle()
    val allContent by viewModel.allContent.collectAsStateWithLifecycle()

    var tab by remember { mutableStateOf(VistaTab.FOR_YOU) }
    var searchOpen by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<dev.drewett.vista.domain.AppEntry?>(null) }
    val topBarFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    val resetSignal by VistaSignals.reset.collectAsStateWithLifecycle()

    LaunchedEffect(resetSignal) {
        if (resetSignal > 0) {
            tab = VistaTab.FOR_YOU
            searchOpen = false
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (tab) {
            VistaTab.FOR_YOU ->
                if (sections.isEmpty()) Loading() else HomeTab(
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

            VistaTab.MOVIES -> ContentGrid("Movies", movies, viewModel::launchContent, contentFocus = contentFocus)
            VistaTab.SHOWS -> ContentGrid("Shows", shows, viewModel::launchContent, contentFocus = contentFocus)
            VistaTab.APPS -> if (apps.isEmpty()) Loading() else AppsScreen(
                apps = apps,
                onLaunch = viewModel::launch,
                onLongPress = { menuApp = it },
                contentFocus = contentFocus,
            )
            VistaTab.PHOTOS -> PhotosScreen(photo = photo, contentFocus = contentFocus)
            VistaTab.SMARTTUBE, VistaTab.MUSIC -> Unit // Launch actions never replace the current screen.
        }

        VistaTopBar(
            selected = tab,
            onSelect = {
                when (it) {
                    VistaTab.SMARTTUBE -> viewModel.launchShortcut(music = false)
                    VistaTab.MUSIC -> viewModel.launchShortcut(music = true)
                    else -> tab = it
                }
            },
            onOpenQuickSettings = onOpenQuickSettings,
            onOpenSearch = { searchOpen = true },
            modifier = Modifier.align(Alignment.TopStart),
            barFocusRequester = topBarFocus,
            contentFocus = contentFocus,
            use24Hour = use24Hour,
        )


        if (searchOpen) {
            SearchScreen(
                apps = apps,
                content = allContent,
                onLaunchApp = viewModel::launch,
                onLaunchContent = viewModel::launchContent,
                onDismiss = { searchOpen = false },
            )
        }

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
