package dev.drewett.vista.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Binder
import dev.drewett.vista.data.immich.ImmichRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers

/** One-time ADB setup without remote typing. This class is never included in release builds. */
class ImmichProvisioningProvider : ContentProvider() {
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        require(Binder.getCallingUid() == 2000 || Binder.getCallingUid() == 0) { "ADB shell only" }
        val repository = ImmichRepository.get(requireNotNull(context))
        return runBlocking(Dispatchers.IO) {
            when (method) {
                "configure" -> {
                    val albums = repository.configure(requireNotNull(arg ?: extras?.getString("server")), requireNotNull(extras?.getString("apiKey")))
                    Bundle().apply { putInt("albumCount", albums.size) }
                }
                "albums" -> Bundle().apply { putStringArray("albums", repository.albums().map { "${it.id}|${it.name}" }.toTypedArray()) }
                "select" -> {
                    val album = repository.albums().firstOrNull { it.id == arg } ?: error("Album unavailable")
                    repository.selectAlbum(album, false)
                    repository.photo()
                    Bundle().apply { putString("status", "photo cached") }
                }
                "configureCalendar" -> {
                    val calendar = dev.drewett.vista.data.calendar.FamilyCalendarRepository.get(requireNotNull(context))
                    calendar.configure(requireNotNull(arg ?: extras?.getString("feed")))
                    Bundle().apply { putInt("eventCount", calendar.state.value.events.size) }
                }
                "weather" -> {
                    val weather = dev.drewett.vista.data.weather.WeatherRepository.get(requireNotNull(context))
                    var failure: String? = null
                    try { weather.configure(requireNotNull(arg)) } catch (error: Exception) { failure = error.javaClass.simpleName }
                    Bundle().apply { putString("city", weather.state.value.city); putInt("temperatureF", weather.state.value.temperatureF ?: 0); putString("failure", failure) }
                }
                "musicClient" -> {
                    dev.drewett.vista.data.music.MusicRepository.get(requireNotNull(context)).saveClient(requireNotNull(arg), requireNotNull(extras?.getString("secret")))
                    Bundle().apply { putString("status", "OAuth client saved") }
                }
                "traktClient" -> {
                    dev.drewett.vista.data.trakt.TraktRepository.get(requireNotNull(context))
                        .saveClient(requireNotNull(arg), extras?.getString("secret").orEmpty())
                    Bundle().apply { putString("status", "Trakt client saved") }
                }
                "traktSignIn" -> {
                    val trakt = dev.drewett.vista.data.trakt.TraktRepository.get(requireNotNull(context))
                    trakt.startSignIn()
                    Bundle().apply {
                        putString("code", trakt.state.value.pairing?.userCode)
                        putString("verificationUrl", trakt.state.value.pairing?.verificationUrl)
                    }
                }
                "traktStatus" -> {
                    val trakt = dev.drewett.vista.data.trakt.TraktRepository.get(requireNotNull(context))
                    if (arg == "refresh") trakt.refreshLibrary(force = true)
                    val current = trakt.state.value
                    Bundle().apply {
                        putBoolean("authorized", current.authorized)
                        putBoolean("configured", current.clientConfigured)
                        putBoolean("loading", current.loading)
                        putInt("movies", current.items.count { it.type == "movie" })
                        putInt("shows", current.items.count { it.type == "show" })
                        putInt("lists", current.lists.size)
                        putInt("posters", current.items.count { it.posterUrl != null })
                        putString("status", current.message)
                    }
                }
                "musicSignIn" -> {
                    val music = dev.drewett.vista.data.music.MusicRepository.get(requireNotNull(context))
                    music.startSignIn()
                    Bundle().apply { putString("code", music.state.value.pairing?.userCode); putString("verificationUrl", music.state.value.pairing?.verificationUrl) }
                }
                "musicStatus" -> {
                    val music = dev.drewett.vista.data.music.MusicRepository.get(requireNotNull(context))
                    if(arg=="refresh") music.refreshLibrary()
                    Bundle().apply { putBoolean("authorized", music.state.value.authorized); putString("channel", music.state.value.channel?.title); putInt("playlists", music.state.value.playlists.size); putInt("artists",music.state.value.artists.size); putInt("homeShelves", music.state.value.shelves.size); putInt("homeCards",music.state.value.shelves.sumOf { it.cards.size }); putString("homeMessage",music.state.value.homeMessage); putString("message", music.state.value.message); putString("code", music.state.value.pairing?.userCode) }
                }
                "musicBrowser" -> {
                    val appContext=requireNotNull(context)
                    val file=java.io.File(appContext.filesDir,"music-browser-import.txt")
                    try {
                        require(file.isFile && file.length() in 1..32768) { "A bounded browser header import is required." }
                        val music=dev.drewett.vista.data.music.MusicRepository.get(appContext)
                        music.configureBrowserSession(file.readText(Charsets.UTF_8))
                        Bundle().apply {
                            putBoolean("browserConnected",music.state.value.browserConnected)
                            putString("browserProfile",music.state.value.browserProfile)
                            putInt("homeShelves",music.state.value.shelves.size)
                            putInt("homeCards",music.state.value.shelves.sumOf { it.cards.size })
                        }
                    } catch(cancelled:kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch(_:Exception) {
                        // JSON/parser exceptions may quote input. Never pass them to Binder/logcat.
                        Bundle().apply { putBoolean("browserConnected",false);putString("status","Browser session could not be verified.") }
                    } finally { file.delete() }
                }
                "musicVerifyMix" -> {
                    val music=dev.drewett.vista.data.music.MusicRepository.get(requireNotNull(context))
                    music.refreshLibrary()
                    val mix=music.state.value.shelves.flatMap { it.cards }.firstOrNull {
                        it.playlistId?.startsWith("RD")==true || it.browseId?.startsWith("VLRD")==true
                    }
                    if(mix!=null) music.selectHomeCard(mix)
                    Bundle().apply { putBoolean("mixFound",mix!=null);putInt("trackCount",if(mix!=null) music.state.value.tracks.size else 0) }
                }
                "ambientStatus" -> {
                    repository.loadGallery(refresh=arg=="refresh")
                    Bundle().apply {
                        putInt("photos",repository.gallery.value.count { !it.isVideo })
                        putInt("videos",repository.gallery.value.count { it.isVideo })
                        putInt("cachedVideos",repository.gallery.value.count { it.isVideo && it.cachedFile?.isFile==true })
                        putInt("cached",repository.gallery.value.count { it.cachedFile?.isFile==true })
                    }
                }
                "ambientVerifyVideo" -> {
                    repository.loadGallery()
                    val videos=repository.gallery.value.filter { it.isVideo }.sortedByDescending { it.cachedFile?.isFile==true }
                    val asset=videos.getOrNull(arg?.toIntOrNull() ?: 0)
                    val result=Bundle().apply { putBoolean("videoFound",asset!=null) }
                    if(asset!=null) {
                        try {
                            val media=repository.ambientAsset(asset.id) ?: error("Media is still loading")
                            val player=android.media.MediaPlayer()
                            try {
                                player.setVolume(0f,0f)
                                player.setDataSource(media.file.absolutePath)
                                val prepared=kotlinx.coroutines.withTimeoutOrNull(15_000) {
                                    kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { pending ->
                                        player.setOnPreparedListener { if(pending.isActive) pending.resumeWith(Result.success(true)) }
                                        player.setOnErrorListener { _,_,_ -> if(pending.isActive) pending.resumeWith(Result.success(false));true }
                                        player.prepareAsync() // Validate only; never start or request audio focus.
                                    }
                                }==true
                                result.putBoolean("prepared",prepared)
                                if(prepared) {
                                    result.putInt("width",player.videoWidth);result.putInt("height",player.videoHeight)
                                    result.putInt("durationMs",player.duration)
                                }
                            } finally { player.release() }
                        } catch(error:Exception) { result.putBoolean("prepared",false);result.putString("failure",if(error is dev.drewett.vista.data.immich.ImmichException) error.message else error.javaClass.simpleName) }
                    }
                    result
                }
                "ambientVideoHeaders" -> {
                    repository.loadGallery()
                    val getSample=arg?.startsWith("get:")==true
                    val asset=repository.gallery.value.filter { it.isVideo }.getOrNull(arg?.substringAfter(':')?.toIntOrNull() ?: 0)
                    val saved=dev.drewett.vista.data.immich.ImmichVault(requireNotNull(context)).read()
                    val result=Bundle().apply { putBoolean("videoFound",asset!=null) }
                    if(asset!=null && saved!=null) {
                        val origin=java.net.URI(saved.server)
                        var target=java.net.URI("${saved.server}/assets/${asset.id}/video/playback")
                        try {
                            for(attempt in 0 until 5) {
                                require(dev.drewett.vista.data.immich.ImmichProtocol.sameOrigin(origin,target))
                                val connection=target.toURL().openConnection() as java.net.HttpURLConnection
                                try {
                                    connection.requestMethod=if(getSample) "GET" else "HEAD";connection.instanceFollowRedirects=false
                                    connection.connectTimeout=8_000;connection.readTimeout=10_000
                                    connection.setRequestProperty("x-api-key",saved.key)
                                    if(getSample) connection.setRequestProperty("Range","bytes=0-1023")
                                    val status=connection.responseCode
                                    if(status in listOf(301,302,303,307,308)) {
                                        target=target.resolve(requireNotNull(connection.getHeaderField("Location")));continue
                                    }
                                    result.putInt("status",status);result.putLong("bytes",connection.contentLengthLong)
                                    result.putString("type",connection.contentType)
                                    if(getSample && status in 200..299) {
                                        val buffer=ByteArray(1024)
                                        try { connection.inputStream.use { result.putInt("sampleBytes",it.read(buffer)) } }
                                        finally { buffer.fill(0) }
                                    }
                                    break
                                } finally { connection.disconnect() }
                            }
                        } catch(error:Exception) { result.putString("failure",error.javaClass.simpleName) }
                    }
                    result
                }
                "publishedChannels" -> {
                    val channels = dev.drewett.vista.data.channels.ChannelsRepository(requireNotNull(context))
                        .loadPreviewPrograms().groupBy { it.packageName to it.channelName }
                    Bundle().apply { putStringArray("channels", channels.map { "${it.key.first}|${it.key.second ?: "Unnamed"}|${it.value.size}" }.toTypedArray()) }
                }
                "photoStatus" -> {
                    repository.awaitReady()
                    var failure: String? = null
                    val file = try { repository.photo() } catch (error: Exception) { failure = if (error is dev.drewett.vista.data.immich.ImmichException) error.message else error.javaClass.simpleName; null }
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    if (file != null) android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
                    Bundle().apply { putInt("width", bounds.outWidth); putInt("height", bounds.outHeight); putInt("gallery", repository.gallery.value.size); putInt("cached", repository.gallery.value.count { it.cachedFile != null }); putInt("fullSizeCached", repository.gallery.value.count { it.cachedFile != null && it.quality == dev.drewett.vista.data.immich.PhotoQuality.FULL_SIZE }); putString("status", repository.status.value); putString("failure", failure) }
                }
                else -> error("Unsupported provisioning method")
            }
        }
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
