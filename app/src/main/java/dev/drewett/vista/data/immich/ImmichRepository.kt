package dev.drewett.vista.data.immich

import android.content.Context
import android.graphics.BitmapFactory
import android.util.AtomicFile
import coil.imageLoader
import coil.memory.MemoryCache
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.selects.select
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.random.Random

/** Stable private catalog/cache. No HTTP operation runs while the state mutex is held. */
class ImmichRepository private constructor(context: Context) {
    private val imageLoader = context.imageLoader
    private val vault = ImmichVault(context)
    private val root = File(context.noBackupFilesDir, "immich-photos")
    private val mutex = Mutex()
    private val catalogMutex = Mutex()
    private val networkSlots = Semaphore(2)
    private val thumbnailSlots = Semaphore(2)
    private val previewSlots = Semaphore(1)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private var prefetchJob: Job? = null
    private var catalogJob: Job? = null
    private var bootstrapJob: Job? = null
    private var previewWarmJob: Job? = null
    private var generation = 0L
    private var saved: SavedImmich? = null
    private var catalog = emptyList<PhotoAsset>()
    private var hidden = emptySet<String>()
    private var currentId: String? = null
    private var catalogComplete = false
    private var lastPrefetchFailure = 0L
    private val downloads = mutableMapOf<String, Mutex>()
    private val _config = MutableStateFlow(ImmichConfig())
    val config: StateFlow<ImmichConfig> = _config.asStateFlow()
    private val _photoRevision = MutableStateFlow(0L)
    val photoRevision: StateFlow<Long> = _photoRevision.asStateFlow()
    private val _gallery = MutableStateFlow<List<PhotoAsset>>(emptyList())
    val gallery: StateFlow<List<PhotoAsset>> = _gallery.asStateFlow()
    private val _hiddenPhotos = MutableStateFlow<List<PhotoAsset>>(emptyList())
    val hiddenPhotos: StateFlow<List<PhotoAsset>> = _hiddenPhotos.asStateFlow()
    private val _hiddenIds = MutableStateFlow<Set<String>>(emptySet())
    val hiddenIds: StateFlow<Set<String>> = _hiddenIds.asStateFlow()
    private val _currentPhoto = MutableStateFlow<PhotoAsset?>(null)
    val currentPhoto: StateFlow<PhotoAsset?> = _currentPhoto.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    init {
        scope.launch {
            mutex.withLock {
                saved = try { vault.read() } catch (_: Exception) { null }
                pruneLocked()
                readIndexLocked()
                publishLocked()
                _config.value = saved?.publicConfig() ?: ImmichConfig(loading = false)
            }
            requestPrefetch()
        }
        scope.launch {
            for (request in requests) {
                val snapshot = snapshotOrNull() ?: continue
                val worker = startPrefetch(snapshot) ?: continue
                worker.join()
            }
        }
    }

    suspend fun awaitReady(): ImmichConfig = config.filter { !it.loading }.first()

    suspend fun configure(serverUrl: String, apiKey: String, allowHttp: Boolean = false): List<ImmichAlbum> = safe {
        awaitReady()
        require(!allowHttp) { "Use an HTTPS Immich server URL." }
        val server = ImmichProtocol.normalizeServer(serverUrl)
        val before = mutex.withLock { saved to generation }
        val old = before.first
        val key = apiKey.trim().ifEmpty { old?.takeIf { it.server == server }?.key
            ?: throw ImmichException("Enter the Immich API key.") }
        require(key.length <= 4096 && key.none { it.isISOControl() }) { "Enter a valid API key." }
        val albums = withTimeout(20_000) { ImmichClient(server, key).albums() }
        mutex.withLock {
            if (generation != before.second) throw ImmichException("The connection changed. Try again.")
            val current = saved
            val same = current?.server == server && current.key == key
            val next = if (same) current!! else SavedImmich(server, key)
            vault.write(next)
            saved = next
            if (!same) resetLocked()
            _config.value = next.publicConfig()
        }
        requestPrefetch()
        albums
    }

    suspend fun albums(): List<ImmichAlbum> = safe {
        awaitReady()
        val before = mutex.withLock { (saved ?: throw ImmichException("Connect to Immich first.")) to generation }
        val albums = withTimeout(20_000) { ImmichClient(before.first.server, before.first.key).albums() }
        mutex.withLock { if (generation != before.second) throw ImmichException("The connection changed. Try again.") }
        albums
    }

    suspend fun selectAlbum(album: ImmichAlbum, fit: Boolean) = safe {
        awaitReady()
        requireNotNull(ImmichProtocol.id(album.id)) { "Choose a valid album." }
        mutex.withLock {
            val old = saved ?: throw ImmichException("Connect to Immich first.")
            val same = old.albumId == album.id
            val next = SavedImmich(old.server, old.key, album.id, album.name.take(256), fit,
                if (same) old.namespace ?: UUID.randomUUID().toString() else UUID.randomUUID().toString(), old.slideshowMode)
            vault.write(next); saved = next
            if (!same) { resetLocked(); readIndexLocked() }
            _config.value = next.publicConfig()
            publishLocked()
        }
        requestPrefetch()
    }

    suspend fun setFit(fit: Boolean) = preferences(fit = fit)
    suspend fun setSlideshowMode(mode: SlideshowMode) = preferences(mode = mode)
    private suspend fun preferences(fit: Boolean? = null, mode: SlideshowMode? = null) = safe {
        awaitReady()
        mutex.withLock {
            val old = saved ?: throw ImmichException("Connect to Immich first.")
            val next = SavedImmich(old.server, old.key, old.albumId, old.albumName, fit ?: old.fit,
                old.namespace, mode ?: old.slideshowMode)
            vault.write(next); saved = next; _config.value = next.publicConfig(); publishLocked()
        }
        requestPrefetch()
    }

    suspend fun loadGallery(refresh: Boolean = false): List<PhotoAsset> = safe {
        awaitReady()
        val snapshot = snapshotOrNull() ?: return@safe emptyList()
        if (mutex.withLock { catalog.isEmpty() }) refreshCatalog(snapshot, firstPageOnly = true)
        if (refresh) requestCatalog(snapshot)
        requestPrefetch()
        _gallery.value
    }

    suspend fun photo(refresh: Boolean = false): File? = choosePhoto(advance = refresh)
    suspend fun nextPhoto(): File? = choosePhoto(advance = true)

    private suspend fun choosePhoto(advance: Boolean): File? = safe {
        awaitReady()
        val snapshot = snapshotOrNull() ?: return@safe null
        val cached = mutex.withLock {
            val eligible = eligibleLocked()
            val cachedCandidates = PhotoPolicy.dailyPool(eligible).filter { it.cachedFile != null }.ifEmpty {
                // An offline anniversary lookup must not block navigation to other cached photos.
                PhotoPolicy.eligible(withFilesLocked(), hidden, SlideshowMode.RANDOM).filter { it.cachedFile != null }
            }
            val current = eligible.firstOrNull { it.id == currentId && it.cachedFile != null }
            val chosen = if (!advance && current != null) current else PhotoPolicy.cachedNext(cachedCandidates, currentId, Random.nextInt())
            chosen?.let { selectLocked(it.id) }
        }
        requestPrefetch()
        startWarmPreviews(snapshot)
        if (cached != null) return@safe cached
        // Reuse the first completed prefetch, rather than queueing a fourth independent download.
        // The foreground deadline includes catalog loading and permit/asset-lock waits; the shared
        // background worker keeps warming the cache if this caller times out or leaves the screen.
        try {
            withTimeout(35_000) {
                val worker = startPrefetch(snapshot) ?: return@withTimeout null
                startBootstrap(snapshot)
                coroutineScope {
                    val ready = async { gallery.first { assets -> assets.any { it.cachedFile?.isFile == true } } }
                    try {
                        select<Unit> {
                            ready.onAwait { }
                            worker.onJoin { }
                        }
                    } finally { ready.cancel() }
                }
                mutex.withLock {
                    if (!isCurrentLocked(snapshot)) return@withLock null
                    val eligible = eligibleLocked().filter { it.cachedFile != null }.ifEmpty {
                        PhotoPolicy.eligible(withFilesLocked(), hidden, SlideshowMode.RANDOM).filter { it.cachedFile != null }
                    }
                    val selected = PhotoPolicy.cachedNext(eligible, currentId, Random.nextInt())
                        ?: throw ImmichException(_status.value ?: "No visible photos were found in this album.")
                    selectLocked(selected.id)
                }
            }
        } catch (_: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            throw ImmichException("Photos are still downloading in the background. Try again shortly.")
        }
    }

    suspend fun showAsset(id: String): File? = safe {
        awaitReady()
        requireNotNull(ImmichProtocol.id(id)) { "Choose a valid photo." }
        val snapshot = snapshotOrNull() ?: return@safe null
        val cached = mutex.withLock {
            if (id in hidden || catalog.none { it.id == id }) throw ImmichException("This photo is hidden or unavailable.")
            (fileFor(snapshot.namespace, id).takeIf { it.isFile }
                ?: bootstrapFor(snapshot.namespace, id).takeIf { it.isFile })?.let { selectLocked(id) }
        }
        if (cached != null) { requestPrefetch(); return@safe cached }
        withTimeout(25_000) { downloadPreview(snapshot, id) }
        val selected = mutex.withLock { if (isCurrentLocked(snapshot) && id !in hidden) selectLocked(id) else null }
        requestPrefetch()
        selected
    }

    /** Visible gallery cells request their own small previews; this never selects a slideshow photo. */
    suspend fun thumbnail(id: String): File? = safe {
        awaitReady()
        requireNotNull(ImmichProtocol.id(id)) { "Choose a valid photo." }
        val snapshot = snapshotOrNull() ?: return@safe null
        val cached = mutex.withLock {
            if (catalog.none { it.id == id } && id !in hidden) return@withLock null
            thumbnailFor(snapshot.namespace, id).takeIf(File::isFile)
                ?: fileFor(snapshot.namespace, id).takeIf(File::isFile)
        }
        if (cached != null) { cached.setLastModified(System.currentTimeMillis()); return@safe cached }
        val lock = mutex.withLock { downloads.getOrPut("thumb:${snapshot.generation}:$id") { Mutex() } }
        lock.withLock thumbLock@ {
            mutex.withLock { thumbnailFor(snapshot.namespace, id).takeIf(File::isFile) }?.let { return@thumbLock it }
            thumbnailSlots.withPermit thumbNetwork@ {
                if (mutex.withLock { !isCurrentLocked(snapshot) || (catalog.none { it.id == id } && id !in hidden) }) return@thumbNetwork null
                val directory = File(root, snapshot.namespace).apply { mkdirs() }
                val temporary = File(directory, ".download-thumb-$id-${UUID.randomUUID()}")
                try {
                    withTimeout(15_000) {
                        temporary.outputStream().use { ImmichClient(snapshot.connection.server, snapshot.connection.key).thumbnail(id, it) }
                    }
                    currentCoroutineContext().ensureActive()
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(temporary.absolutePath, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw ImmichException("This preview cannot be displayed.")
                    mutex.withLock thumbCommit@ {
                        if (!isCurrentLocked(snapshot)) return@thumbCommit null
                        val result = thumbnailFor(snapshot.namespace, id)
                        if (!temporary.renameTo(result)) throw ImmichException("Cannot save the photo preview.")
                        evictThumbnailsLocked(snapshot.namespace, result)
                        publishLocked()
                        result
                    }
                } finally { temporary.delete() }
            }
        }
    }

    suspend fun cachedPhoto(excluding: File? = null): File? = safe {
        awaitReady()
        mutex.withLock {
            val cached = eligibleLocked().filter { it.cachedFile != null }.ifEmpty {
                PhotoPolicy.eligible(withFilesLocked(), hidden, SlideshowMode.RANDOM).filter { it.cachedFile != null }
            }
            (cached.firstOrNull { it.cachedFile != excluding } ?: cached.firstOrNull())?.cachedFile
        }
    }

    suspend fun hideCurrent() { currentPhoto.value?.id?.let { hideAsset(it) } }
    suspend fun hideAsset(id: String) = changeHidden(id, true)
    suspend fun unhideAsset(id: String) = changeHidden(id, false)
    suspend fun unhideAll() = safe {
        awaitReady()
        mutex.withLock { hidden = emptySet(); writeIndexLocked(); publishLocked(); _photoRevision.value += 1 }
        requestPrefetch()
    }
    private suspend fun changeHidden(id: String, hide: Boolean) = safe {
        awaitReady()
        requireNotNull(ImmichProtocol.id(id)) { "Choose a valid photo." }
        mutex.withLock {
            hidden = if (hide) hidden + id else hidden - id
            if (hide) saved?.namespace?.let {
                deleteFileLocked(fileFor(it, id)); deleteFileLocked(thumbnailFor(it, id)); deleteFileLocked(bootstrapFor(it, id))
            }
            if (currentId == id && hide) currentId = null
            writeIndexLocked(); publishLocked(); _photoRevision.value += 1
        }
        requestPrefetch()
    }

    suspend fun disconnect() = safe {
        awaitReady()
        mutex.withLock { vault.delete(); saved = null; resetLocked(); _config.value = ImmichConfig(loading = false) }
    }

    private suspend fun backgroundCatalog(snapshot: Snapshot) {
        try { refreshCatalog(snapshot) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutex.withLock { if (isCurrentLocked(snapshot)) _status.value = "Offline: showing cached photos." } }
    }

    private suspend fun requestCatalog(snapshot: Snapshot) {
        val job = mutex.withLock {
            if (!isCurrentLocked(snapshot) || catalogJob?.isActive == true) return@withLock null
            scope.launch(start = CoroutineStart.LAZY) { backgroundCatalog(snapshot) }.also { catalogJob = it }
        }
        job?.start()
    }

    /** Page one publishes promptly. Follow explicit continuation tokens, never page-local totals. */
    private suspend fun refreshCatalog(snapshot: Snapshot, firstPageOnly: Boolean = false) {
        if (mutex.withLock { !isCurrentLocked(snapshot) }) return
        if (firstPageOnly && mutex.withLock { catalog.isNotEmpty() }) return
        try {
            val client = ImmichClient(snapshot.connection.server, snapshot.connection.key)
            // The catalog lock covers one metadata request, never all pages or any photo download.
            val first = catalogMutex.withLock {
                if (firstPageOnly && mutex.withLock { catalog.isNotEmpty() }) return@withLock null
                mutex.withLock { _loading.value = true; _status.value = "Loading album…" }
                requestPage(client, snapshot.albumId, 1)
            } ?: return
            var collected = first.assets
            var continuation = first.nextPage
            commitCatalog(snapshot, collected)
            if (!firstPageOnly) {
                val visited = mutableSetOf(1)
                while (continuation != null && visited.size < 5 && collected.size < 500) {
                    val page = continuation ?: break
                    if (!visited.add(page)) break
                    currentCoroutineContext().ensureActive()
                    if (mutex.withLock { !isCurrentLocked(snapshot) }) return
                    val response = requestPage(client, snapshot.albumId, page)
                    collected = (collected + response.assets)
                        .distinctBy { it.id }.take(500)
                    continuation = response.nextPage
                    commitCatalog(snapshot, collected)
                }
                mutex.withLock { if (isCurrentLocked(snapshot)) catalogComplete = true }
            }
            mutex.withLock { if (isCurrentLocked(snapshot)) _status.value = if (continuation != null)
                "Showing ${collected.size} photos from this album; more photos are available on Immich." else null }
        } finally { mutex.withLock { if (isCurrentLocked(snapshot)) _loading.value = false } }
    }

    private suspend fun requestPage(client: ImmichClient, albumId: String, page: Int): PhotoPage = try {
        withTimeout(20_000) { client.catalogPage(albumId, page) }
    } catch (_: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        throw ImmichException("Album refresh took too long. Cached photos remain available.")
    }

    private suspend fun commitCatalog(snapshot: Snapshot, assets: List<PhotoAsset>) = mutex.withLock {
        if (!isCurrentLocked(snapshot)) return@withLock
        val retained = catalog.filter {
            fileFor(snapshot.namespace, it.id).isFile || bootstrapFor(snapshot.namespace, it.id).isFile || it.id in hidden || it.id == currentId ||
                downloads["${snapshot.generation}:${it.id}"]?.isLocked == true ||
                downloads["preview:${snapshot.generation}:${it.id}"]?.isLocked == true
        }
        catalog = (assets + retained).distinctBy { it.id }.take(600)
        writeIndexLocked(); publishLocked()
    }

    private fun requestPrefetch() { requests.trySend(Unit) }
    private suspend fun startPrefetch(snapshot: Snapshot): Job? = mutex.withLock {
        if (!isCurrentLocked(snapshot)) return@withLock null
        prefetchJob?.takeIf { !it.isCompleted && !it.isCancelled }
            ?: scope.launch { prefetch(snapshot) }.also { prefetchJob = it }
    }

    private suspend fun startBootstrap(snapshot: Snapshot) = mutex.withLock {
        if (!isCurrentLocked(snapshot) || bootstrapJob?.isActive == true) return@withLock
        bootstrapJob = scope.launch {
            try {
                withTimeout(25_000) {
                    gallery.first { it.isNotEmpty() }
                    val id = mutex.withLock {
                        if (!isCurrentLocked(snapshot) || eligibleLocked().any { it.cachedFile != null }) return@withLock null
                        eligibleLocked().firstOrNull()?.id
                    } ?: return@withTimeout
                    downloadPreview(snapshot, id)
                }
            } catch (cancelled: CancellationException) { /* Shared background warming continues independently. */ }
            catch (_: Exception) { /* The full-size prefetch path reports safe errors and retains its cache. */ }
        }
    }

    private suspend fun startWarmPreviews(snapshot: Snapshot) = mutex.withLock {
        if (!isCurrentLocked(snapshot) || previewWarmJob?.isActive == true) return@withLock
        previewWarmJob = scope.launch {
            gallery.first { it.isNotEmpty() }
            val ids = mutex.withLock { PhotoPolicy.dailyPool(eligibleLocked()).map { it.id } }
            for (id in ids) {
                val needed = mutex.withLock { isCurrentLocked(snapshot) && id !in hidden &&
                    !fileFor(snapshot.namespace,id).isFile && !bootstrapFor(snapshot.namespace,id).isFile }
                if (!needed) continue
                try { withTimeout(20_000) { downloadPreview(snapshot,id) } }
                catch (_: TimeoutCancellationException) { currentCoroutineContext().ensureActive() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Skip one failed image and keep warming the pool. */ }
            }
        }
    }

    private suspend fun downloadPreview(snapshot: Snapshot, id: String): File? {
        val lock = mutex.withLock { downloads.getOrPut("preview:${snapshot.generation}:$id") { Mutex() } }
        return lock.withLock previewLock@ {
            val existing = mutex.withLock {
                if (!isCurrentLocked(snapshot) || id in hidden) return@withLock null
                fileFor(snapshot.namespace, id).takeIf(File::isFile) ?: bootstrapFor(snapshot.namespace, id).takeIf(File::isFile)
            }
            if (existing != null) return@previewLock existing
            previewSlots.withPermit previewNetwork@ {
                if (mutex.withLock { !isCurrentLocked(snapshot) || id in hidden }) return@previewNetwork null
                val directory = File(root, snapshot.namespace).apply { mkdirs() }
                val temporary = File(directory, ".download-bootstrap-$id-${UUID.randomUUID()}")
                try {
                    temporary.outputStream().use { ImmichClient(snapshot.connection.server, snapshot.connection.key).preview(id, it) }
                    currentCoroutineContext().ensureActive()
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(temporary.absolutePath, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw ImmichException("This photo preview cannot be displayed.")
                    mutex.withLock previewCommit@ {
                        if (!isCurrentLocked(snapshot) || id in hidden) return@previewCommit null
                        fileFor(snapshot.namespace, id).takeIf(File::isFile)?.let { return@previewCommit it }
                        val file = bootstrapFor(snapshot.namespace, id)
                        if (!temporary.renameTo(file)) throw ImmichException("Cannot save the photo preview.")
                        directory.listFiles().orEmpty().filter {
                            it.name.startsWith("bootstrap-") && it != file &&
                                currentId?.let { current -> it == bootstrapFor(snapshot.namespace, current) } != true
                        }.sortedByDescending { it.lastModified() }.drop(PhotoPolicy.DAILY_POOL_SIZE - 1).forEach(::deleteFileLocked)
                        evictLocked(snapshot.namespace,id)
                        _status.value = "Showing a quick preview while the full-size photo downloads."
                        publishLocked()
                        file
                    }
                } finally { temporary.delete() }
            }
        }
    }
    private suspend fun prefetch(snapshot: Snapshot) {
        if (System.currentTimeMillis() - lastPrefetchFailure < 10_000) return
        try {
            if (mutex.withLock { catalog.isEmpty() }) refreshCatalog(snapshot, firstPageOnly = true)
            startWarmPreviews(snapshot)
            if (mutex.withLock { !catalogComplete }) requestCatalog(snapshot)
            while (true) {
                val targets = mutex.withLock {
                    if (!isCurrentLocked(snapshot)) return
                    val daily = PhotoPolicy.dailyPool(eligibleLocked())
                    val current = withFilesLocked().firstOrNull { it.id == currentId }
                    PhotoPolicy.prefetchCandidates((daily + listOfNotNull(current)).distinctBy { it.id }, hidden,
                        saved?.slideshowMode ?: SlideshowMode.RANDOM, currentId).map { it.id }
                }
                if (targets.isEmpty()) return
                val successes = coroutineScope {
                    targets.map { id -> async {
                        try { download(snapshot, id) != null }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) {
                            mutex.withLock { if (isCurrentLocked(snapshot)) _status.value =
                                if (error is ImmichException) error.message else "Some photos could not load. Cached photos remain available." }
                            false
                        }
                    } }.map { it.await() }
                }
                if (successes.none { it }) {
                    lastPrefetchFailure = System.currentTimeMillis()
                    return
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            lastPrefetchFailure = System.currentTimeMillis()
            mutex.withLock { if (isCurrentLocked(snapshot)) _status.value = "Offline: showing cached photos." }
        }
    }

    private suspend fun download(snapshot: Snapshot, id: String): File? {
        val lock = mutex.withLock { downloads.getOrPut("${snapshot.generation}:$id") { Mutex() } }
        return lock.withLock assetLock@ {
            val existing = mutex.withLock {
                if (!isCurrentLocked(snapshot) || id in hidden) return@withLock null
                fileFor(snapshot.namespace, id).takeIf { it.isFile }
            }
            if (existing != null) return@assetLock existing
            networkSlots.withPermit network@ {
                if (mutex.withLock { !isCurrentLocked(snapshot) || id in hidden }) return@network null
                val directory = File(root, snapshot.namespace).apply { mkdirs() }
                val staging = File(directory, ".download-$id-${UUID.randomUUID()}")
                try {
                    try {
                        withTimeout(90_000) {
                            staging.outputStream().use { ImmichClient(snapshot.connection.server, snapshot.connection.key).image(id, it) }
                        }
                    } catch (_: TimeoutCancellationException) {
                        currentCoroutineContext().ensureActive()
                        throw ImmichException("This photo took too long to download. Cached photos remain available.")
                    }
                    currentCoroutineContext().ensureActive()
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(staging.absolutePath, bounds)
                    if (bounds.outWidth !in 1..100_000 || bounds.outHeight !in 1..100_000)
                        throw ImmichException("This photo format cannot be displayed on this device.")
                    mutex.withLock commit@ {
                        if (!isCurrentLocked(snapshot) || id in hidden) return@commit null
                        val file = fileFor(snapshot.namespace, id)
                        if (!staging.renameTo(file)) throw ImmichException("Cannot save this photo to the private cache.")
                        val upgradedCurrent = id == currentId
                        deleteFileLocked(bootstrapFor(snapshot.namespace, id))
                        evictLocked(snapshot.namespace, id)
                        publishLocked()
                        // Upgrade the existing current photo in place, without advancing to another ID.
                        if (upgradedCurrent) { _photoRevision.value += 1; _status.value = null }
                        file
                    }
                } finally { staging.delete() }
            }
        }
    }

    private fun eligibleLocked() = PhotoPolicy.eligible(withFilesLocked(), hidden, saved?.slideshowMode ?: SlideshowMode.RANDOM)
    private fun withFilesLocked(): List<PhotoAsset> {
        val namespace = saved?.namespace
        return catalog.map { asset ->
            val full = namespace?.let { fileFor(it, asset.id).takeIf(File::isFile) }
            val file = full ?: namespace?.let { bootstrapFor(it, asset.id).takeIf(File::isFile) }
            asset.copy(cachedFile = file, hidden = asset.id in hidden,
                thumbFile = namespace?.let { thumbnailFor(it, asset.id).takeIf(File::isFile) } ?: file,
                quality = if (full != null) PhotoQuality.FULL_SIZE else PhotoQuality.PREVIEW)
        }
    }
    private fun selectLocked(id: String): File? {
        val asset = withFilesLocked().firstOrNull { it.id == id && !it.hidden } ?: return null
        val file = asset.cachedFile ?: return null
        val changed = currentId != id
        currentId = id; file.setLastModified(System.currentTimeMillis())
        writeIndexLocked(); publishLocked()
        if (changed) _photoRevision.value += 1
        return file
    }
    private fun publishLocked() {
        val assets = withFilesLocked()
        _gallery.value = assets.filterNot { it.hidden }
        _hiddenPhotos.value = hidden.map { id -> assets.firstOrNull { it.id == id } ?: PhotoAsset(id, hidden = true) }
        _hiddenIds.value = hidden.toSet()
        _currentPhoto.value = assets.firstOrNull { it.id == currentId && !it.hidden && it.cachedFile != null }
    }

    private fun fileFor(namespace: String, id: String) = File(File(root, namespace), "asset-$id.image")
    private fun thumbnailFor(namespace: String, id: String) = File(File(root, namespace), "thumb-$id.image")
    private fun bootstrapFor(namespace: String, id: String) = File(File(root, namespace), "bootstrap-$id.image")
    private fun indexLocked(): AtomicFile? = saved?.namespace?.let {
        AtomicFile(File(File(root, it).apply { mkdirs() }, "catalog.json"))
    }
    private fun writeIndexLocked() {
        val file = indexLocked() ?: return
        val assets = JSONArray().apply { catalog.forEach { put(JSONObject().put("id", it.id).put("date", it.date)) } }
        val bytes = JSONObject().put("assets", assets).put("hidden", JSONArray(hidden.toList()))
            .put("currentId", currentId).toString().toByteArray(Charsets.UTF_8)
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
    private fun readIndexLocked() {
        catalog = emptyList(); hidden = emptySet(); currentId = null; catalogComplete = false
        val index = indexLocked() ?: return
        try {
            if (!index.baseFile.exists() || index.baseFile.length() > 1024 * 1024) return
            val json = JSONObject(index.readFully().toString(Charsets.UTF_8))
            val values = json.optJSONArray("assets") ?: JSONArray()
            catalog = buildList {
                for (i in 0 until minOf(values.length(), 600)) {
                    val item = values.optJSONObject(i) ?: continue
                    val id = ImmichProtocol.id(item.optString("id")) ?: continue
                    add(PhotoAsset(id, item.optString("date").takeIf { it.length == 10 }))
                }
            }.distinctBy { it.id }
            val exclusions = json.optJSONArray("hidden") ?: JSONArray()
            hidden = buildSet { for (i in 0 until exclusions.length()) ImmichProtocol.id(exclusions.optString(i))?.let(::add) }
            currentId = ImmichProtocol.id(json.optString("currentId"))
        } catch (_: Exception) { /* Invalid local metadata cannot restore another account's photos. */ }
    }
    private fun resetLocked() {
        generation += 1; prefetchJob?.cancel(); catalogJob?.cancel(); bootstrapJob?.cancel(); previewWarmJob?.cancel(); downloads.clear()
        catalog = emptyList(); hidden = emptySet(); currentId = null; catalogComplete = false
        if (root.exists()) root.walkTopDown().filter { it.isFile }.forEach(::deleteFileLocked)
        root.deleteRecursively(); lastPrefetchFailure = 0
        _loading.value = false; _status.value = null; publishLocked(); _photoRevision.value += 1
    }
    private fun pruneLocked() {
        root.listFiles().orEmpty().forEach { directory ->
            if (directory.name != saved?.namespace) directory.deleteRecursively()
            else directory.listFiles().orEmpty().filter { it.name.startsWith(".download-") || it.name.endsWith(".new") }.forEach { it.delete() }
        }
    }
    private fun evictLocked(namespace: String, protectId: String) {
        val files = File(root, namespace).listFiles().orEmpty().filter {
            (it.name.startsWith("asset-") || it.name.startsWith("bootstrap-")) && it.name.endsWith(".image") }
        val protected = setOfNotNull(fileFor(namespace, protectId).name, bootstrapFor(namespace, protectId).name,
            currentId?.let { fileFor(namespace, it).name }, currentId?.let { bootstrapFor(namespace, it).name })
        val evict = PhotoCachePolicy.evictionKeys(files.map { CachedPhotoEntry(it.name, it.length(), it.lastModified()) }, protected)
        files.filter { it.name in evict }.forEach(::deleteFileLocked)
        File(root, namespace).listFiles().orEmpty().filter { it.name.startsWith("photo-") }.forEach(::deleteFileLocked)
    }
    private fun evictThumbnailsLocked(namespace: String, protect: File) {
        val files = File(root, namespace).listFiles().orEmpty().filter { it.name.startsWith("thumb-") && it.name.endsWith(".image") }
        val evict = PhotoCachePolicy.evictionKeys(files.map { CachedPhotoEntry(it.name, it.length(), it.lastModified()) },
            setOf(protect.name), 30L * 1024 * 1024, 128)
        files.filter { it.name in evict }.forEach(::deleteFileLocked)
    }
    private fun deleteFileLocked(file: File) { imageLoader.memoryCache?.remove(MemoryCache.Key(file.absolutePath)); file.delete() }
    private suspend fun snapshotOrNull(): Snapshot? {
        awaitReady()
        return mutex.withLock {
            val c = saved ?: return@withLock null
            Snapshot(c, c.albumId ?: return@withLock null, c.namespace ?: return@withLock null, generation)
        }
    }
    private fun isCurrentLocked(s: Snapshot) = generation == s.generation && saved?.namespace == s.namespace
    private class Snapshot(val connection: SavedImmich, val albumId: String, val namespace: String, val generation: Long) {
        override fun toString() = "ImmichSnapshot(redacted)"
    }
    private suspend fun <T> safe(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try { block() }
        catch (_: TimeoutCancellationException) { currentCoroutineContext().ensureActive(); throw ImmichException("Immich is taking too long. Cached photos remain available.") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: ImmichException) { throw error }
        catch (error: IllegalArgumentException) { throw ImmichException(error.message?.takeIf { it.startsWith("Use ") || it.startsWith("Enter ") || it.startsWith("Choose ") }
            ?: "Immich returned an unsupported response.") }
        catch (_: Exception) { throw ImmichException("Cannot reach Immich. Cached photos remain available.") }
    }
    companion object {
        @Volatile private var instance: ImmichRepository? = null
        fun get(context: Context): ImmichRepository = instance ?: synchronized(this) {
            instance ?: ImmichRepository(context.applicationContext).also { instance = it }
        }
    }
}
