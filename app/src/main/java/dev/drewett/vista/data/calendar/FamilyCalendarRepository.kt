package dev.drewett.vista.data.calendar

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class FamilyCalendarState(val configured: Boolean = false, val loading: Boolean = true,
    val events: List<FamilyEvent> = emptyList(), val stale: Boolean = false, val message: String? = null)

/** No private feed URL is exposed in public state, logs, intents or saved-state Bundles. */
class FamilyCalendarRepository private constructor(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "family-calendar-v1"))
    private val mutex = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private var feed: String? = null
    private var cached: String? = null
    private var lastRefresh = 0L
    private val _state = MutableStateFlow(FamilyCalendarState())
    val state = _state.asStateFlow()
    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            mutex.withLock {
                try {
                    if (file.baseFile.exists()) {
                        val saved = JSONObject(decrypt(file.readFully()))
                        feed = validateUrl(saved.getString("url")).toString()
                        cached = saved.getString("ics")
                        _state.value = FamilyCalendarState(true, false, FamilyCalendarParser.parse(cached!!), true, "Saved calendar; waiting for refresh.")
                    } else _state.value = FamilyCalendarState(loading = false)
                } catch (_: Exception) {
                    feed = null; cached = null
                    _state.value = FamilyCalendarState(loading = false, message = "Reconnect the Family calendar.")
                } finally { ready.complete(Unit) }
            }
        }
    }

    suspend fun configure(url: String) = io {
        val checked = validateUrl(url.trim()).toString()
        val text = download(checked)
        val events = FamilyCalendarParser.parse(text)
        write(checked, text)
        feed = checked; cached = text; lastRefresh = System.currentTimeMillis()
        _state.value = FamilyCalendarState(true, false, events, false, "Family calendar connected.")
    }

    suspend fun refresh(force: Boolean = false) = io {
        val url = feed ?: return@io
        if (!force && System.currentTimeMillis() - lastRefresh < 15 * 60_000) return@io
        _state.value = _state.value.copy(loading = true)
        try {
            val text = download(url)
            val events = FamilyCalendarParser.parse(text)
            write(url, text)
            cached = text; lastRefresh = System.currentTimeMillis()
            _state.value = FamilyCalendarState(true, false, events)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { _state.value = _state.value.copy(loading = false, stale = true, message = "Offline or unavailable; showing saved events.") }
    }

    suspend fun disconnect() = io {
        file.delete(); feed = null; cached = null; lastRefresh = 0
        _state.value = FamilyCalendarState(loading = false)
    }

    private suspend fun <T> io(block: suspend () -> T): T {
        ready.await()
        return withContext(Dispatchers.IO) { mutex.withLock { block() } }
    }

    private fun validateUrl(value: String): URI {
        require(value.length in 1..8192) { "Enter a private HTTPS iCal link." }
        val uri = try { URI(value) } catch (_: Exception) { throw IllegalArgumentException("Enter a valid HTTPS iCal link.") }
        require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawFragment == null) { "Use a private HTTPS iCal link without login credentials." }
        return uri
    }

    private suspend fun download(url: String): String {
        val origin = validateUrl(url)
        var target = origin
        repeat(5) {
            currentCoroutineContext().ensureActive()
            require(target.scheme.equals(origin.scheme, true) && target.host.equals(origin.host, true) &&
                (if (target.port == -1) 443 else target.port) == (if (origin.port == -1) 443 else origin.port) && target.rawUserInfo == null && target.rawFragment == null) { "The calendar redirected to another server." }
            val connection = target.toURL().openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 8_000; connection.readTimeout = 10_000; connection.useCaches = false
                connection.setRequestProperty("Accept", "text/calendar")
                when (connection.responseCode) {
                    301,302,303,307,308 -> { target = target.resolve(connection.getHeaderField("Location") ?: error("Invalid calendar redirect")); return@repeat }
                    in 200..299 -> Unit
                    else -> error("Calendar unavailable")
                }
                require(connection.contentLengthLong <= 2 * 1024 * 1024) { "Calendar too large" }
                val output = ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 2 * 1024 * 1024) { "Calendar too large" }
                        output.write(buffer,0,count)
                    }
                }
                return output.toString(Charsets.UTF_8.name())
            } finally { connection.disconnect() }
        }
        error("Too many redirects")
    }

    private fun write(url: String, ics: String) {
        val bytes = JSONObject().put("url",url).put("ics",ics).toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        val encrypted = try { cipher.doFinal(bytes) } finally { bytes.fill(0) }
        val stream = file.startWrite()
        try { stream.write(ByteBuffer.allocate(12+encrypted.size).put(cipher.iv).put(encrypted).array()); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
    private fun decrypt(bytes: ByteArray): String {
        require(bytes.size in 28..(6 * 1024 * 1024))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12))) }
        val plain = cipher.doFinal(bytes,12,bytes.size-12)
        return try { plain.toString(Charsets.UTF_8) } finally { plain.fill(0) }
    }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("vista.family.calendar.v1",null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance("AES","AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("vista.family.calendar.v1",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    companion object {
        @Volatile private var instance: FamilyCalendarRepository? = null
        fun get(context: Context): FamilyCalendarRepository = instance ?: synchronized(this) { instance ?: FamilyCalendarRepository(context.applicationContext).also { instance = it } }
    }
}
