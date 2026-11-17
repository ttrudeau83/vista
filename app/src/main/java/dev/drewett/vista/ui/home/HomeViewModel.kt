package dev.drewett.vista.ui.home

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.tvprovider.media.tv.TvContractCompat
import dev.drewett.vista.data.apps.AppRepository
import dev.drewett.vista.data.channels.ChannelsRepository
import dev.drewett.vista.data.favourites.FavouritesRepository
import dev.drewett.vista.data.usage.LaunchHistoryRepository
import dev.drewett.vista.data.usage.UsageRepository
import dev.drewett.vista.domain.AppEntry
import dev.drewett.vista.domain.AppSection
import dev.drewett.vista.domain.ContentCard
import dev.drewett.vista.domain.ContentSection
import dev.drewett.vista.domain.HomeSection
import dev.drewett.vista.domain.RowType
import dev.drewett.vista.domain.PublishedContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private data class HomeData(
    val apps: List<AppEntry> = emptyList(),
    val recents: List<String> = emptyList(),
    val watchNext: List<ContentCard> = emptyList(),
    val preview: List<ContentCard> = emptyList(),
    val loaded: Boolean = false,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val appRepository = AppRepository(application)
    private val usageRepository = UsageRepository(application)
    private val favouritesRepository = FavouritesRepository(application)
    private val launchHistoryRepository = LaunchHistoryRepository(application)
    private val channelsRepository = ChannelsRepository(application)
    private val data = MutableStateFlow(HomeData())
    private val refreshMutex = Mutex()
    private var refreshJob: Job? = null
    private val started = SharingStarted.WhileSubscribed(5_000)

    val apps = data.map { it.apps }.stateIn(viewModelScope, started, emptyList())
    val loaded = data.map { it.loaded }.stateIn(viewModelScope, started, false)
    val favourites = favouritesRepository.favourites.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val sections = combine(data, launchHistoryRepository.recent, favourites) { current, launched, favs ->
        buildSections(current, launched.ifEmpty { current.recents }, favs)
    }.stateIn(viewModelScope, started, emptyList())
    val allContent: StateFlow<List<ContentCard>> = data.map { PublishedContent.distinct(it.watchNext + it.preview) }
        .stateIn(viewModelScope, started, emptyList())
    val movies = allContent.map { cards -> cards.filter { it.isMovie } }.stateIn(viewModelScope, started, emptyList())
    val shows = allContent.map { cards -> cards.filter { it.isShow } }.stateIn(viewModelScope, started, emptyList())
    val continueWatching = data.map { continueCards(it) }.stateIn(viewModelScope, started, emptyList())
    val smartTubeSections = data.map { current ->
        providerSections(current.preview.filter { PublishedContent.isSmartTube(it.packageName) }, current.apps)
    }.stateIn(viewModelScope, started, emptyList())

    private val packageChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refresh() }
    }
    private val providerChanges = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { refresh() }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(application, packageChanges, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        listOf(TvContractCompat.Channels.CONTENT_URI, TvContractCompat.PreviewPrograms.CONTENT_URI,
            TvContractCompat.WatchNextPrograms.CONTENT_URI).forEach { uri ->
            runCatching { application.contentResolver.registerContentObserver(uri, true, providerChanges) }
        }
        refresh()
    }

    /** Coalesces package/provider bursts. Every published snapshot uses fresh installed-package data. */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(180)
            try { withContext(Dispatchers.IO) {
                refreshMutex.withLock {
                    val freshApps = appRepository.loadApps()
                    val enabled = appRepository.enabledPackages()
                    channelsRepository.invalidateIcons()
                    val watch = PublishedContent.available(channelsRepository.loadContinueWatching(), enabled)
                    val preview = PublishedContent.available(channelsRepository.loadPreviewPrograms(), enabled)
                    val recents = usageRepository.recentPackages()
                    currentCoroutineContext().ensureActive()
                    data.value = HomeData(freshApps, recents, watch, preview, loaded = true)
                }
            } } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                // Retain the last usable snapshot if PackageManager itself is temporarily unavailable.
                data.value = data.value.copy(loaded = true)
            }
        }
    }

    override fun onCleared() {
        val application = getApplication<Application>()
        runCatching { application.unregisterReceiver(packageChanges) }
        runCatching { application.contentResolver.unregisterContentObserver(providerChanges) }
        super.onCleared()
    }

    fun launch(entry: AppEntry) {
        if (!appRepository.isPackageEnabled(entry.packageName)) { refresh(); return }
        viewModelScope.launch { launchHistoryRepository.record(entry.packageName) }
        appRepository.launch(entry)
    }

    fun launchSeamless(entry: AppEntry) {
        if (!appRepository.isPackageEnabled(entry.packageName)) { refresh(); return }
        viewModelScope.launch { launchHistoryRepository.record(entry.packageName) }
        appRepository.launch(entry, seamless = true)
    }

    fun launchContent(card: ContentCard) {
        if (card.packageName?.let { appRepository.isPackageEnabled(it) } != true) { refresh(); return }
        card.packageName?.let { pkg -> viewModelScope.launch { launchHistoryRepository.record(pkg) } }
        if (!channelsRepository.launchContent(card)) {
            data.value.apps.firstOrNull { it.packageName == card.packageName }?.let(::launch)
        }
    }

    fun openAppInfo(packageName: String) = appRepository.openAppInfo(packageName)
    fun uninstall(packageName: String) = appRepository.uninstall(packageName)
    fun toggleFavourite(entry: AppEntry) {
        viewModelScope.launch { favouritesRepository.toggle(entry.packageName) }
    }

    private fun continueCards(current: HomeData) = PublishedContent.distinct(current.watchNext +
        current.preview.filter { PublishedContent.isContinueWatchingChannel(it.channelName) })

    private fun providerSections(cards: List<ContentCard>, apps: List<AppEntry>): List<ContentSection> {
        val byPackage = apps.associateBy { it.packageName }
        return cards.groupBy { it.packageName to it.channelId }.map { (owner, items) ->
            val app = byPackage[owner.first]
            val name = items.firstNotNullOfOrNull { it.channelName }
            ContentSection(key = "preview:${owner.first}:${owner.second}",
                title = listOfNotNull(app?.label ?: owner.first, name).distinct().joinToString(" · "),
                items = PublishedContent.distinct(items).take(30), providerIcon = app?.icon)
        }
    }

    private fun buildSections(current: HomeData, recents: List<String>, favourites: Set<String>): List<HomeSection> {
        val apps = current.apps
        val byPackage = apps.associateBy { it.packageName }
        val result = mutableListOf<HomeSection>()
        continueCards(current).takeIf { it.isNotEmpty() }?.let { result += ContentSection("continue", "Continue watching", it) }
        recents.mapNotNull { byPackage[it] }.take(12).takeIf { it.isNotEmpty() }
            ?.let { result += AppSection(RowType.JUMP_BACK_IN, "Jump back in", it) }
        apps.filter { it.packageName in favourites }.takeIf { it.isNotEmpty() }
            ?.let { result += AppSection(RowType.FAVOURITES, "Favourites", it) }
        // Channel names come only from the publisher; a Nuvio trend label is never fabricated.
        result += providerSections(current.preview, apps)
        apps.filter { it.category == ApplicationInfo.CATEGORY_VIDEO }.takeIf { it.isNotEmpty() }
            ?.let { result += AppSection(RowType.WATCH, "Watch", it) }
        apps.filter { it.category == ApplicationInfo.CATEGORY_AUDIO }.takeIf { it.isNotEmpty() }
            ?.let { result += AppSection(RowType.LISTEN, "Listen", it) }
        apps.filter { it.isGame }.takeIf { it.isNotEmpty() }
            ?.let { result += AppSection(RowType.PLAY, "Play", it) }
        apps.filter { it.category !in setOf(ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_AUDIO) && !it.isGame }
            .takeIf { it.isNotEmpty() }?.let { result += AppSection(RowType.APPS, "Your apps", it) }
        return result
    }
}
