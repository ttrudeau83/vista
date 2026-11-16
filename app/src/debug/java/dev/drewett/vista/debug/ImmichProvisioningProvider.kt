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
                "musicSignIn" -> {
                    val music = dev.drewett.vista.data.music.MusicRepository.get(requireNotNull(context))
                    music.startSignIn()
                    Bundle().apply { putString("code", music.state.value.pairing?.userCode); putString("verificationUrl", music.state.value.pairing?.verificationUrl) }
                }
                "musicStatus" -> {
                    val music = dev.drewett.vista.data.music.MusicRepository.get(requireNotNull(context))
                    Bundle().apply { putBoolean("authorized", music.state.value.authorized); putString("channel", music.state.value.channel?.title); putInt("playlists", music.state.value.playlists.size); putString("message", music.state.value.message); putString("code", music.state.value.pairing?.userCode) }
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
