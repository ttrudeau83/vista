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

    suspend fun catalogPage(albumId: String, page: Int): PhotoPage {
        requireNotNull(ImmichProtocol.id(albumId))
        require(page in 1..1_000_000)
        val filter = JSONObject().put("albumIds", JSONArray().put(albumId))
            .put("isTrashed", false).put("size", 100).put("page", page).toString()
        return ImmichProtocol.photoPage(json("/search/metadata", filter))
    }

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

    suspend fun imageCandidates(albumId: String): List<String> {
        requireNotNull(ImmichProtocol.id(albumId))
        val candidates = mutableListOf<String>()
        try {
            val buckets = ImmichProtocol.buckets(json("/timeline/buckets?albumId=${encode(albumId)}&isTrashed=false"))
            for (bucket in buckets.shuffled().take(8)) {
                val body = json("/timeline/bucket?albumId=${encode(albumId)}&timeBucket=${encode(bucket)}&isTrashed=false")
                candidates += ImmichProtocol.bucketImages(body).shuffled().take(100)
                if (candidates.distinct().size >= 2) return candidates.distinct().take(100)
            }
        } catch (_: ImmichResponseTooLarge) { /* Fall back to a bounded search page. */ }
        val filter = JSONObject().put("albumIds", JSONArray().put(albumId)).put("type", "IMAGE")
            .put("isTrashed", false).put("size", 100).put("page", 1).toString()
        return (candidates + ImmichProtocol.searchImages(json("/search/metadata", filter))).distinct().ifEmpty {
            throw ImmichException("No supported photos were found in this album.")
        }
    }

    suspend fun image(assetId: String, output: OutputStream) {
        requireNotNull(ImmichProtocol.id(assetId))
        // Immich 2.7.5 supports fullsize; it may redirect to the original when appropriate.
        // This requires asset.download for those originals, rather than silently displaying a tiny preview.
        request("/assets/$assetId/thumbnail?size=fullsize", 40L * 1024 * 1024, output, image = true)
    }

    suspend fun thumbnail(assetId: String, output: OutputStream) {
        requireNotNull(ImmichProtocol.id(assetId))
        request("/assets/$assetId/thumbnail?size=thumbnail", 2L * 1024 * 1024, output, image = true)
    }

    suspend fun preview(assetId: String, output: OutputStream) {
        requireNotNull(ImmichProtocol.id(assetId))
        request("/assets/$assetId/thumbnail?size=preview", 8L * 1024 * 1024, output, image = true)
    }

    suspend fun video(assetId: String, output: OutputStream) {
        requireNotNull(ImmichProtocol.id(assetId))
        // Immich serves its encoded playback file where available, falling back to the original.
        request("/assets/$assetId/video/playback", 128L * 1024 * 1024, output, image = false, video = true)
    }

    private suspend fun json(path: String, body: String? = null): String {
        val output = ByteArrayOutputStream()
        request(path, 2L * 1024 * 1024, output, image = false, body = body)
        return output.toString(Charsets.UTF_8.name())
    }

    private suspend fun request(path: String, maxBytes: Long, output: OutputStream, image: Boolean, body: String? = null, video: Boolean = false) {
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
                connection.setRequestProperty("Accept", if (image) "image/*" else if(video) "video/*" else "application/json")
                if(video) connection.setRequestProperty("Range","bytes=0-")
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
                    403 -> throw ImmichException("Check the API key permissions: album.read, asset.read, asset.view and asset.download for full-size photos.")
                    in 200..299 -> Unit
                    else -> throw ImmichException("Immich request failed (HTTP $status).")
                }
                if (connection.contentLengthLong > maxBytes) {
                    if (!image && !video) throw ImmichResponseTooLarge()
                    throw ImmichMediaUnusable("This media file is too large to cache.")
                }
                val type = connection.contentType.orEmpty().substringBefore(';').lowercase()
                if (image && !type.startsWith("image/") && type != "application/octet-stream") {
                    throw ImmichMediaUnusable("Immich did not return a supported photo.")
                }
                if (video && !type.startsWith("video/") && type != "application/octet-stream") {
                    throw ImmichMediaUnusable("Immich did not return a supported video.")
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
                            if (!image && !video) throw ImmichResponseTooLarge()
                            throw ImmichMediaUnusable("The server response exceeded the cache limit.")
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
/** This item can never be cached or shown on this device; retrying it cannot succeed. */
internal class ImmichMediaUnusable(message: String) : ImmichException(message)
