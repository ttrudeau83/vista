package dev.drewett.vista.data.immich

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.UUID

data class ImmichAlbum(val id: String, val name: String)

/** Public settings deliberately contain no authentication material. */
data class ImmichConfig(
    val serverUrl: String = "",
    val configured: Boolean = false,
    val albumId: String? = null,
    val albumName: String? = null,
    val fit: Boolean = false,
    val allowHttp: Boolean = false,
    val loading: Boolean = true,
    val namespace: String? = null,
)

open class ImmichException(message: String) : Exception(message)

/** Protocol helpers are independent of Android so URL and response boundaries can be tested. */
object ImmichProtocol {
    fun normalizeServer(input: String, allowHttp: Boolean = false): String {
        require(input.length <= 2048) { "Enter a shorter Immich server URL." }
        val uri = try { URI(input.trim()) } catch (_: Exception) {
            throw IllegalArgumentException("Enter a valid Immich server URL.")
        }
        val scheme = uri.scheme?.lowercase()
        require(scheme == "https" || (allowHttp && scheme == "http")) {
            "Use an HTTPS Immich server URL."
        }
        require(!uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null && uri.port in -1..65535 && uri.port != 0) {
            "Use a server URL without credentials, query parameters or a fragment."
        }
        require(!uri.rawPath.orEmpty().contains('%') &&
            uri.path.orEmpty().split('/').none { it == "." || it == ".." }) {
            "Use a plain server base path."
        }
        val path = uri.path.orEmpty().trimEnd('/').let { if (it.endsWith("/api")) it else "$it/api" }
        return URI(scheme, null, uri.host.lowercase(), uri.port, path, null, null).toASCIIString()
    }

    fun sameOrigin(first: URI, second: URI): Boolean =
        first.scheme.equals(second.scheme, ignoreCase = true) &&
            first.host != null && first.host.equals(second.host, ignoreCase = true) &&
            port(first) == port(second) && second.rawUserInfo == null

    private fun port(uri: URI): Int = if (uri.port != -1) uri.port else
        if (uri.scheme.equals("https", ignoreCase = true)) 443 else 80

    fun albums(json: String): List<ImmichAlbum> {
        val values = JSONArray(json)
        return buildList {
            for (index in 0 until values.length()) {
                val item = values.optJSONObject(index) ?: continue
                val id = id(item.optString("id")) ?: continue
                val name = item.optString("albumName").trim().take(256).takeIf { it.isNotEmpty() } ?: continue
                add(ImmichAlbum(id, name))
            }
        }.distinctBy { it.id }
    }

    fun firstAlbumImage(json: String): String? {
        val assets = JSONObject(json).optJSONArray("assets") ?: return null
        return firstImage(assets)
    }

    fun firstSearchImage(json: String): String? = JSONObject(json).optJSONObject("assets")
        ?.optJSONArray("items")?.let(::firstImage)

    fun searchImages(json: String): List<String> = JSONObject(json).optJSONObject("assets")
        ?.optJSONArray("items")?.let(::images).orEmpty()

    private fun firstImage(assets: JSONArray): String? {
        return images(assets).firstOrNull()
    }

    private fun images(assets: JSONArray): List<String> = buildList {
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            if (asset.optString("type") != "IMAGE" || asset.optBoolean("isTrashed", false)) continue
            id(asset.optString("id"))?.let(::add)
        }
    }.distinct()

    fun buckets(json: String): List<String> {
        val values = JSONArray(json)
        return buildList {
            for (index in 0 until values.length()) {
                val value = values.optJSONObject(index)?.optString("timeBucket")?.trim().orEmpty()
                if (value.isNotBlank() && value.length <= 64 && value.none { it.isISOControl() }) add(value)
            }
        }.distinct()
    }

    fun firstBucketImage(json: String): String? {
        return bucketImages(json).firstOrNull()
    }

    fun bucketImages(json: String): List<String> {
        val bucket = JSONObject(json)
        val ids = bucket.optJSONArray("id") ?: return emptyList()
        val images = bucket.optJSONArray("isImage") ?: return emptyList()
        val trashed = bucket.optJSONArray("isTrashed") ?: return emptyList()
        if (ids.length() != images.length() || ids.length() != trashed.length()) return emptyList()
        return buildList {
            for (index in 0 until ids.length()) {
                if (images.opt(index) != true || trashed.opt(index) != false) continue
                id(ids.optString(index))?.let(::add)
            }
        }.distinct()
    }

    internal fun id(value: String): String? = try {
        UUID.fromString(value).toString().takeIf { it.equals(value, ignoreCase = true) }
    } catch (_: IllegalArgumentException) { null }
}

/** Never immediately repeat when the available candidates include another valid image. */
object PhotoSequence {
    fun chooseNext(candidates: List<String>, previous: String?, randomIndex: Int): String? {
        val unique = candidates.filter { ImmichProtocol.id(it) != null }.distinct()
        val choices = unique.filter { it != previous }.ifEmpty { unique }
        if (choices.isEmpty()) return null
        return choices[Math.floorMod(randomIndex, choices.size)]
    }
}
