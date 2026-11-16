package dev.drewett.vista.data.immich

import android.content.Context
import android.graphics.BitmapFactory
import android.util.AtomicFile
import coil.imageLoader
import coil.memory.MemoryCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.UUID
import kotlin.random.Random

/** Shared photo source for the home screen; network and disk work never run on Main. */
class ImmichRepository private constructor(context: Context) {
    private val imageLoader = context.imageLoader
    private val vault = ImmichVault(context)
    private val root = File(context.noBackupFilesDir, "immich-photos")
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saved: SavedImmich? = null
    private var sequenceNamespace: String? = null
    private var sequenceCandidates = emptyList<String>()
    private var sequenceRemaining = emptyList<String>()
    private var sequencePrevious: String? = null
    private val _config = MutableStateFlow(ImmichConfig())
    val config: StateFlow<ImmichConfig> = _config.asStateFlow()
    private val _photoRevision = MutableStateFlow(0L)
    val photoRevision: StateFlow<Long> = _photoRevision.asStateFlow()

    init {
        scope.launch {
            mutex.withLock {
                saved = try { vault.read() } catch (_: Exception) { null }
                _config.value = saved?.publicConfig() ?: ImmichConfig(loading = false)
                // An unavailable/deleted credential must never resurrect photos from an older account.
                if (saved == null) purgePhotos() else pruneInterruptedDownloads()
            }
        }
    }

    suspend fun awaitReady(): ImmichConfig = config.filter { !it.loading }.first()

    /** Tests authentication through album discovery, then saves the connection atomically. */
    suspend fun configure(serverUrl: String, apiKey: String, allowHttp: Boolean = false): List<ImmichAlbum> = io {
        require(!allowHttp) { "Use an HTTPS Immich server URL." }
        val server = ImmichProtocol.normalizeServer(serverUrl)
        val previous = saved
        val key = apiKey.trim().ifEmpty {
            previous?.takeIf { it.server == server }?.key
                ?: throw ImmichException("Enter the Immich API key.")
        }
        require(key.length <= 4096 && key.none { it.isISOControl() }) { "Enter a valid API key." }
        val albums = ImmichClient(server, key).albums()
        currentCoroutineContext().ensureActive()
        val unchanged = previous?.server == server && previous.key == key
        val next = if (unchanged) previous!! else SavedImmich(server, key)
        vault.write(next)
        saved = next
        _config.value = next.publicConfig()
        if (!unchanged) {
            purgePhotos()
            _photoRevision.value += 1
        }
        albums
    }

    suspend fun albums(): List<ImmichAlbum> = io {
        val connection = requireConnection()
        ImmichClient(connection.server, connection.key).albums()
    }

    suspend fun selectAlbum(album: ImmichAlbum, fit: Boolean) = io {
        requireNotNull(ImmichProtocol.id(album.id)) { "Choose a valid album." }
        require(album.name.isNotBlank()) { "Choose a valid album." }
        val previous = requireConnection()
        val unchanged = previous.albumId == album.id
        val next = SavedImmich(previous.server, previous.key, album.id, album.name, fit,
            if (unchanged) previous.namespace ?: UUID.randomUUID().toString() else UUID.randomUUID().toString())
        vault.write(next)
        saved = next
        _config.value = next.publicConfig()
        if (!unchanged) {
            purgePhotos()
            _photoRevision.value += 1
        }
    }

    suspend fun setFit(fit: Boolean) = io {
        val previous = requireConnection()
        val next = SavedImmich(previous.server, previous.key, previous.albumId, previous.albumName, fit, previous.namespace)
        vault.write(next)
        saved = next
        _config.value = next.publicConfig()
    }

    /** Returns the private last-good file immediately, unless explicitly asked to refresh. */
    suspend fun photo(refresh: Boolean = false): File? = loadPhoto(refresh, advance = false)

    /** A different album photo for the system screensaver; uses the same bounded private cache. */
    suspend fun nextPhoto(): File? = loadPhoto(refresh = true, advance = true)

    /** Offline slides can alternate the retained successful photos without issuing network requests. */
    suspend fun cachedPhoto(excluding: File? = null): File? = io {
        val namespace = saved?.namespace ?: return@io null
        val files = cachedFiles(File(root, namespace))
        files.firstOrNull { it != excluding } ?: files.firstOrNull()
    }

    private suspend fun loadPhoto(refresh: Boolean, advance: Boolean): File? = io {
        val connection = saved ?: return@io null
        val albumId = connection.albumId ?: return@io null
        val namespace = connection.namespace ?: return@io null
        val directory = File(root, namespace)
        val cached = cachedFiles(directory).firstOrNull()
        if (!refresh && cached != null) return@io cached
        if (!directory.exists() && !directory.mkdirs()) throw ImmichException("Cannot create the private photo cache.")
        val staging = File(directory, ".download-${UUID.randomUUID()}")
        try {
            val client = ImmichClient(connection.server, connection.key)
            withTimeout(30_000) {
                val imageId = if (advance) nextAsset(client, albumId, namespace) else client.firstImage(albumId)
                staging.outputStream().use { client.image(imageId, it) }
            }
            currentCoroutineContext().ensureActive()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(staging.absolutePath, bounds)
            if (bounds.outWidth !in 1..100_000 || bounds.outHeight !in 1..100_000) {
                throw ImmichException("This photo format cannot be displayed on this device.")
            }
            // A new filename also prevents Coil from serving stale data after a manual refresh.
            val destination = File(directory, "photo-${UUID.randomUUID()}.jpg")
            val atomic = AtomicFile(destination)
            val output = atomic.startWrite()
            try {
                staging.inputStream().use { it.copyTo(output) }
                currentCoroutineContext().ensureActive()
                atomic.finishWrite(output)
            } catch (error: Exception) {
                atomic.failWrite(output)
                throw error
            }
            // Keep only the newest two successful images (at most 40 MiB); failed refreshes retain both.
            cachedFiles(directory).filter { it != destination }.drop(1).forEach(::deletePhoto)
            _photoRevision.value += 1
            destination
        } finally { staging.delete() }
    }

    private suspend fun nextAsset(client: ImmichClient, albumId: String, namespace: String): String {
        if (sequenceNamespace != namespace || sequenceRemaining.isEmpty()) {
            if (sequenceNamespace != namespace) sequencePrevious = null
            sequenceNamespace = namespace
            sequenceCandidates = client.imageCandidates(albumId)
            sequenceRemaining = sequenceCandidates
        }
        val chosen = PhotoSequence.chooseNext(sequenceRemaining, sequencePrevious, Random.nextInt())
            ?: throw ImmichException("No supported photos were found in this album.")
        sequenceRemaining = sequenceRemaining.filter { it != chosen }
        sequencePrevious = chosen
        return chosen
    }

    suspend fun disconnect() = io {
        vault.delete()
        saved = null
        _config.value = ImmichConfig(loading = false)
        purgePhotos()
        _photoRevision.value += 1
    }

    private fun cachedFiles(directory: File): List<File> = directory.listFiles()
        ?.filter { it.isFile && it.name.startsWith("photo-") && it.name.endsWith(".jpg") && it.length() in 1..(20L * 1024 * 1024) }
        ?.sortedByDescending { it.lastModified() }.orEmpty()

    private fun requireConnection() = saved ?: throw ImmichException("Connect to Immich first.")

    private fun deletePhoto(file: File) {
        imageLoader.memoryCache?.remove(MemoryCache.Key(file.absolutePath))
        file.delete()
    }

    private fun purgePhotos() {
        sequenceNamespace = null
        sequenceCandidates = emptyList()
        sequenceRemaining = emptyList()
        sequencePrevious = null
        if (root.exists()) root.walkTopDown().filter { it.isFile }.forEach {
            imageLoader.memoryCache?.remove(MemoryCache.Key(it.absolutePath))
        }
        root.deleteRecursively()
    }

    private fun pruneInterruptedDownloads() {
        root.listFiles().orEmpty().forEach { directory ->
            if (directory.name != saved?.namespace || !directory.isDirectory) {
                if (directory.isDirectory) {
                    directory.walkTopDown().filter { it.isFile }.forEach(::deletePhoto)
                    directory.deleteRecursively()
                } else deletePhoto(directory)
            } else {
                directory.listFiles().orEmpty().filter { !it.name.startsWith("photo-") || !it.name.endsWith(".jpg") }
                    .forEach(::deletePhoto)
                cachedFiles(directory).drop(2).forEach(::deletePhoto)
            }
        }
    }

    private suspend fun <T> io(block: suspend () -> T): T {
        awaitReady()
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                try { block() }
                catch (_: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    throw ImmichException("Immich took too long to return a photo. Try refreshing again.")
                }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: ImmichException) { throw error }
                catch (error: IllegalArgumentException) {
                    // Only our validation messages are surfaced, never transport exception text.
                    throw ImmichException(error.message?.takeIf { it.startsWith("Use ") || it.startsWith("Enter ") || it.startsWith("Choose ") }
                        ?: "Immich returned an unsupported response.")
                }
                catch (_: Exception) { throw ImmichException("Cannot reach Immich or read its response. Check the server and connection.") }
            }
        }
    }

    companion object {
        @Volatile private var instance: ImmichRepository? = null
        fun get(context: Context): ImmichRepository = instance ?: synchronized(this) {
            instance ?: ImmichRepository(context.applicationContext).also { instance = it }
        }
    }
}
