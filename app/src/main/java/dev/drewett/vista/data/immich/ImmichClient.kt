package dev.drewett.vista.data.immich

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/** Small, bounded client. Never logs requests, headers, response bodies or credentials. */
internal class ImmichClient(private val server: String, private val apiKey: String) {
    suspend fun albums(): List<ImmichAlbum> = ImmichProtocol.albums(json("/albums"))

    suspend fun firstImage(albumId: String): String {
        requireNotNull(ImmichProtocol.id(albumId))
        // Timeline columns are supported in both verified versions (2.7.5 and 3.4.1).
        // Avoid album detail: older servers embed every asset in that unbounded response.
        try {
            val buckets = ImmichProtocol.buckets(json("/timeline/buckets?albumId=${encode(albumId)}&isTrashed=false"))
            for (bucket in buckets.take(48)) {
                val body = json("/timeline/bucket?albumId=${encode(albumId)}&timeBucket=${encode(bucket)}&isTrashed=false")
                ImmichProtocol.firstBucketImage(body)?.let { return it }
            }
        } catch (_: ImmichResponseTooLarge) {
            // A single very large bucket can exceed our memory budget. Ask for one image instead.
        }
        val filter = JSONObject().put("albumIds", JSONArray().put(albumId)).put("type", "IMAGE")
            .put("isTrashed", false).put("size", 1).put("page", 1).toString()
        ImmichProtocol.firstSearchImage(json("/search/metadata", filter))?.let { return it }
        throw ImmichException("No supported photos were found in this album.")
    }

    suspend fun image(assetId: String, output: OutputStream) {
        requireNotNull(ImmichProtocol.id(assetId))
        request("/assets/$assetId/thumbnail?size=preview", 20L * 1024 * 1024, output, image = true)
    }

    private suspend fun json(path: String, body: String? = null): String {
        val output = ByteArrayOutputStream()
        request(path, 2L * 1024 * 1024, output, image = false, body = body)
        return output.toString(Charsets.UTF_8.name())
    }

    private suspend fun request(path: String, maxBytes: Long, output: OutputStream, image: Boolean, body: String? = null) {
        val origin = URI(server)
        var target = URI(server + path)
        repeat(5) {
            currentCoroutineContext().ensureActive()
            if (!ImmichProtocol.sameOrigin(origin, target) || target.rawFragment != null) {
                throw ImmichException("The server redirected to another address. Use its final HTTPS URL.")
            }
            val connection = target.toURL().openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 8_000
                connection.readTimeout = 10_000
                connection.useCaches = false
                connection.setRequestProperty("x-api-key", apiKey)
                connection.setRequestProperty("Accept", if (image) "image/*" else "application/json")
                if (body != null) {
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                when (val status = connection.responseCode) {
                    301, 302, 303, 307, 308 -> {
                        val location = connection.getHeaderField("Location")
                            ?: throw ImmichException("The server returned an invalid redirect.")
                        target = try { target.resolve(location) } catch (_: Exception) {
                            throw ImmichException("The server returned an invalid redirect.")
                        }
                        return@repeat
                    }
                    401 -> throw ImmichException("Immich rejected the API key. Enter a valid key.")
                    403 -> throw ImmichException("The API key needs permission to read albums and view assets.")
                    in 200..299 -> Unit
                    else -> throw ImmichException("Immich request failed (HTTP $status).")
                }
                if (connection.contentLengthLong > maxBytes) {
                    if (!image) throw ImmichResponseTooLarge()
                    throw ImmichException("This photo is too large to cache.")
                }
                val type = connection.contentType.orEmpty().substringBefore(';').lowercase()
                if (image && !type.startsWith("image/") && type != "application/octet-stream") {
                    throw ImmichException("Immich did not return a supported photo.")
                }
                connection.inputStream.use { input ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        total += count
                        if (total > maxBytes) {
                            if (!image) throw ImmichResponseTooLarge()
                            throw ImmichException("The server response exceeded the cache limit.")
                        }
                        output.write(buffer, 0, count)
                    }
                    if (total == 0L) throw ImmichException("Immich returned an empty response.")
                }
                currentCoroutineContext().ensureActive()
                return
            } finally {
                connection.disconnect()
            }
        }
        throw ImmichException("The server redirected too many times.")
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
}

internal class ImmichResponseTooLarge : ImmichException("The server response is too large.")
