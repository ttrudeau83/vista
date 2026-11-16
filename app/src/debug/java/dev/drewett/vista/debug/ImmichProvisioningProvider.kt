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
