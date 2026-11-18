package dev.drewett.vista.data.tv

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Switches inputs on an LG webOS TV through its local network control ("LG Connect Apps").
 * The box itself cannot do this over HDMI-CEC: Android only lets system apps address other inputs.
 *
 * Protocol: SSAP over a WebSocket on the TV (wss :3001, older sets ws :3000). The first connection asks
 * the viewer to allow Vista on the TV; the TV then returns a client key that is kept in [LgTvVault].
 * The registration manifest is LG's own from the Apache-2.0 Connect SDK, as used by aiowebostv.
 */
internal class LgTvRemote(context: Context) {
    private val app = context.applicationContext
    private val vault = LgTvVault(app)
    private val lock = Mutex()

    sealed class Outcome {
        data object Switched : Outcome()
        data class Failed(val message: String) : Outcome()
    }

    val pairing: LgTvPairing? get() = vault.read()

    fun forget() { vault.delete() }

    /** True when the stored key was granted the current permission set (otherwise the next request re-pairs). */
    val paired: Boolean get() = vault.read()?.let { it.clientKey != null && it.permissions == PERMISSIONS_VERSION } == true

    /**
     * Switches to HDMI [port] (1-4), finding and pairing with the TV first if needed. Some webOS versions
     * refuse `tv/switchInput` to third-party clients, so the TV's own HDMI input app is the fallback.
     */
    suspend fun switchToHdmi(port: Int): Outcome = request(listOf(
        "ssap://tv/switchInput" to JSONObject().put("inputId", "HDMI_$port"),
        "ssap://system.launcher/launch" to JSONObject().put("id", "com.webos.app.hdmi$port"),
    )).fold({ Outcome.Switched }, { Outcome.Failed(it.message ?: "The TV did not switch inputs.") })

    /** Finds the TV (if not known) and pairs; [fresh] always shows the allow prompt on the TV again. */
    suspend fun pair(fresh: Boolean = false): Outcome = request(emptyList(), fresh)
        .fold({ Outcome.Switched }, { Outcome.Failed(it.message ?: "Could not pair with the TV.") })

    /** The TV's external inputs as (id, label), e.g. ("HDMI_2", "HDMI 2 · Xbox"). */
    suspend fun inputs(): List<Pair<String, String>> = request(listOf("ssap://tv/getExternalInputList" to null)).getOrNull()?.let { payload ->
        val devices = payload.optJSONArray("devices") ?: JSONArray()
        (0 until devices.length()).mapNotNull { devices.optJSONObject(it) }.map { d ->
            val id = d.optString("id"); val label = d.optString("label").ifBlank { id }
            id to if (label == id) id.replace('_', ' ') else "${id.replace('_', ' ')} · $label"
        }
    }.orEmpty()

    /** Runs [calls] in order on one paired connection and returns the first that succeeds. */
    private suspend fun request(calls: List<Pair<String, JSONObject?>>, fresh: Boolean = false): Result<JSONObject> = lock.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val known = vault.read()
                val host = known?.host?.takeIf { reachable(it) } ?: discover()
                    ?: error("Couldn't find the LG TV on the network. Make sure the TV is on and on the same network, and that LG's phone-control setting (\"LG Connect Apps\" or \"Mobile TV On\", in the TV's network or connection settings) is on.")
                // The key belongs to the TV, not its address, so it survives the TV getting a new IP. A key
                // granted for an older (smaller) permission set is dropped so the TV prompts again.
                val key = known?.clientKey?.takeIf { !fresh && known.permissions == PERMISSIONS_VERSION }
                val session = Session.open(host)
                try {
                    val newKey = session.register(key)
                    if (newKey != known?.clientKey || host != known.host || known.permissions != PERMISSIONS_VERSION)
                        vault.write(LgTvPairing(host, newKey, PERMISSIONS_VERSION))
                    var failure: Throwable? = null
                    for ((uri, payload) in calls) {
                        try { return@runCatching session.call(uri, payload) }
                        catch (e: Exception) { failure = e; Log.w(TAG, "$uri refused: ${e.message}") }
                    }
                    failure?.let { throw IllegalStateException("The TV refused: ${it.message}. Try Pair again in Vista settings › TV inputs.") }
                    JSONObject()
                } finally { session.close() }
            }.onFailure { Log.w(TAG, "TV request failed: ${it.message}") }
        }
    }

    private fun reachable(host: String): Boolean = listOf(3001, 3000).any { port ->
        runCatching { java.net.Socket().use { it.connect(InetSocketAddress(host, port), 1500) }; true }.getOrDefault(false)
    }

    /** SSDP search for a webOS "second screen" service on the local network. */
    @SuppressLint("MissingPermission")
    private fun discover(): String? {
        val wifi = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicast = wifi?.createMulticastLock("vista-lgtv")?.apply { setReferenceCounted(false); acquire() }
        try {
            MulticastSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(0))
                socket.soTimeout = 1200
                val search = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\n" +
                    "ST: urn:lge-com:service:webos-second-screen:1\r\n\r\n").toByteArray()
                val group = InetAddress.getByName("239.255.255.250")
                repeat(3) { attempt ->
                    socket.send(DatagramPacket(search, search.size, group, 1900))
                    val deadline = System.currentTimeMillis() + 1500L * (attempt + 1)
                    val buffer = ByteArray(2048)
                    while (System.currentTimeMillis() < deadline) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        try { socket.receive(packet) } catch (_: SocketTimeoutException) { break }
                        val text = String(packet.data, 0, packet.length)
                        val address = packet.address
                        if (text.contains("webos-second-screen", ignoreCase = true) && address.isSiteLocalAddress) return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "TV discovery failed: ${e.message}")
        } finally { multicast?.release() }
        return null
    }

    /** One WebSocket conversation with the TV. */
    private class Session private constructor(private val socket: WebSocket, private val incoming: Channel<JSONObject>) {
        private var nextId = 1

        /** Registers, waiting for the on-TV prompt when there is no key yet. Returns the client key. */
        suspend fun register(clientKey: String?): String {
            val payload = JSONObject(REGISTRATION_PAYLOAD)
            if (clientKey != null) payload.put("client-key", clientKey)
            socket.send(JSONObject().put("type", "register").put("id", "register_0").put("payload", payload).toString())
            return withTimeout(if (clientKey == null) 60_000 else 10_000) {
                while (true) {
                    val message = incoming.receive()
                    if (message.optString("id") != "register_0") continue
                    when (message.optString("type")) {
                        "registered" -> return@withTimeout message.optJSONObject("payload")?.optString("client-key")?.ifBlank { null }
                            ?: clientKey ?: error("The TV paired but sent no key.")
                        "error" -> error(if (clientKey != null) "The TV no longer accepts Vista. Pair again in Vista settings › TV inputs."
                            else "The TV declined Vista (${message.optString("error")}).")
                    }
                }
                @Suppress("UNREACHABLE_CODE") error("unreachable")
            }
        }

        suspend fun call(uri: String, payload: JSONObject?): JSONObject {
            val id = "vista_${nextId++}"
            socket.send(JSONObject().put("type", "request").put("id", id).put("uri", uri).apply { if (payload != null) put("payload", payload) }.toString())
            return withTimeout(8_000) {
                while (true) {
                    val message = incoming.receive()
                    if (message.optString("id") != id) continue
                    val body = message.optJSONObject("payload") ?: JSONObject()
                    if (message.optString("type") == "error" || !body.optBoolean("returnValue", true))
                        error(body.optString("errorText").ifBlank { message.optString("error").ifBlank { "The TV refused the request." } })
                    return@withTimeout body
                }
                @Suppress("UNREACHABLE_CODE") error("unreachable")
            }
        }

        fun close() { socket.close(1000, null) }

        companion object {
            suspend fun open(host: String): Session {
                var last: Throwable? = null
                for (url in listOf("wss://$host:3001", "ws://$host:3000")) {
                    val incoming = Channel<JSONObject>(Channel.UNLIMITED)
                    val opened = Channel<Result<Unit>>(1)
                    val socket = client(host).newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) { opened.trySend(Result.success(Unit)) }
                        override fun onMessage(webSocket: WebSocket, text: String) { runCatching { incoming.trySend(JSONObject(text)) } }
                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            opened.trySend(Result.failure(t)); incoming.close(t)
                        }
                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { incoming.close() }
                    })
                    val result = runCatching { withTimeout(5_000) { opened.receive() } }.getOrElse { Result.failure(it) }
                    if (result.isSuccess) return Session(socket, incoming)
                    socket.cancel(); last = result.exceptionOrNull()
                }
                throw IllegalStateException("Couldn't connect to the TV (${last?.message}).")
            }

            /**
             * webOS serves a self-signed certificate on :3001. Trust is relaxed only for this connection to
             * the TV's own private-network address, and only SSAP messages are exchanged.
             */
            private fun client(host: String): OkHttpClient {
                require(InetAddress.getByName(host).isSiteLocalAddress) { "The TV must be on the local network." }
                val trustAll = object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                }
                val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), SecureRandom()) }
                return OkHttpClient.Builder()
                    .sslSocketFactory(ssl.socketFactory, trustAll)
                    .hostnameVerifier { name, _ -> name == host }
                    .connectTimeout(4, TimeUnit.SECONDS)
                    .readTimeout(0, TimeUnit.SECONDS)
                    .build()
            }
        }
    }

    companion object {
        private const val TAG = "VistaLgTv"
        /** Bump when the requested permissions change, so existing pairings are asked again. */
        const val PERMISSIONS_VERSION = 2

        private const val SIGNATURE = "eyJhbGdvcml0aG0iOiJSU0EtU0hBMjU2Iiwia2V5SWQiOiJ0ZXN0LXNpZ25pbm" +
            "ctY2VydCIsInNpZ25hdHVyZVZlcnNpb24iOjF9.hrVRgjCwXVvE2OOSpDZ58hR" +
            "+59aFNwYDyjQgKk3auukd7pcegmE2CzPCa0bJ0ZsRAcKkCTJrWo5iDzNhMBWRy" +
            "aMOv5zWSrthlf7G128qvIlpMT0YNY+n/FaOHE73uLrS/g7swl3/qH/BGFG2Hu4" +
            "RlL48eb3lLKqTt2xKHdCs6Cd4RMfJPYnzgvI4BNrFUKsjkcu+WD4OO2A27Pq1n" +
            "50cMchmcaXadJhGrOqH5YmHdOCj5NSHzJYrsW0HPlpuAx/ECMeIZYDh6RMqaFM" +
            "2DXzdKX9NmmyqzJ3o/0lkk/N97gfVRLW5hA29yeAwaCViZNCP8iC9aO0q9fQoj" +
            "oa7NQnAtw=="

        /** LG Connect SDK registration manifest (Apache-2.0), with the full permission set aiowebostv requests. */
        private val REGISTRATION_PAYLOAD: String = JSONObject()
            .put("forcePairing", false)
            .put("pairingType", "PROMPT")
            .put("manifest", JSONObject()
                .put("appVersion", "1.1")
                .put("manifestVersion", 1)
                .put("permissions", JSONArray(listOf("LAUNCH", "LAUNCH_WEBAPP", "APP_TO_APP", "CLOSE", "TEST_OPEN", "TEST_PROTECTED",
                    "CONTROL_AUDIO", "CONTROL_DISPLAY", "CONTROL_INPUT_JOYSTICK", "CONTROL_INPUT_MEDIA_RECORDING",
                    "CONTROL_INPUT_MEDIA_PLAYBACK", "CONTROL_INPUT_TV", "CONTROL_POWER", "CONTROL_TV_SCREEN", "READ_APP_STATUS",
                    "READ_CURRENT_CHANNEL", "READ_INPUT_DEVICE_LIST", "READ_NETWORK_STATE", "READ_RUNNING_APPS",
                    "READ_TV_CHANNEL_LIST", "WRITE_NOTIFICATION_TOAST", "READ_POWER_STATE", "READ_COUNTRY_INFO",
                    "CONTROL_INPUT_TEXT", "CONTROL_MOUSE_AND_KEYBOARD", "READ_INSTALLED_APPS", "READ_SETTINGS")))
                .put("signatures", JSONArray().put(JSONObject().put("signature", SIGNATURE).put("signatureVersion", 1)))
                .put("signed", JSONObject()
                    .put("appId", "com.lge.test")
                    .put("created", "20140509")
                    .put("localizedAppNames", JSONObject().put("", "LG Remote App").put("ko-KR", "리모컨 앱").put("zxx-XX", "ЛГ Rэмotэ AПП"))
                    .put("localizedVendorNames", JSONObject().put("", "LG Electronics"))
                    .put("permissions", JSONArray(listOf("TEST_SECURE", "CONTROL_INPUT_TEXT", "CONTROL_MOUSE_AND_KEYBOARD",
                        "READ_INSTALLED_APPS", "READ_LGE_SDX", "READ_NOTIFICATIONS", "SEARCH", "WRITE_SETTINGS",
                        "WRITE_NOTIFICATION_ALERT", "CONTROL_POWER", "READ_CURRENT_CHANNEL", "READ_RUNNING_APPS",
                        "READ_UPDATE_INFO", "UPDATE_FROM_REMOTE_APP", "READ_LGE_TV_INPUT_EVENTS", "READ_TV_CURRENT_TIME")))
                    .put("serial", "2f930e2d2cfe083771f68e4fe7bb07")
                    .put("vendorId", "com.lge")))
            .toString()
    }
}
