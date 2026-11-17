package dev.drewett.vista.data.trakt

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

data class TraktPairing(val userCode: String, val verificationUrl: String, val expiresAt: Long)
data class TraktList(val id: Long, val name: String)
data class TraktItem(val type: String, val traktId: Long, val title: String, val year: Int?,
    val overview: String?, val imdbId: String?, val tmdbId: Long?, val posterUrl: String?, val rating: Double?)
data class TraktState(val loading: Boolean = false, val clientConfigured: Boolean = false,
    val authorized: Boolean = false, val pairing: TraktPairing? = null, val message: String? = null,
    val items: List<TraktItem> = emptyList(), val lists: List<TraktList> = emptyList(), val selectedListId: Long? = null)

internal object TraktProtocol {
    /** A single, explicitly typed item prevents a movie/show ID collision from removing both. */
    fun removalBody(type: String, traktId: Long): JSONObject {
        require(type == "movie" || type == "show")
        require(traktId > 0)
        val key = if (type == "movie") "movies" else "shows"
        return JSONObject().put(key, JSONArray().put(JSONObject().put("ids", JSONObject().put("trakt", traktId))))
    }

    fun removalPath(listId: Long?): String {
        require(listId == null || listId > 0)
        return if (listId == null) "/sync/watchlist/remove" else "/users/me/lists/$listId/items/remove"
    }

    /** Do not hide a cached title merely because a write returned HTTP 200. */
    fun removalConfirmed(type: String, response: JSONObject): Boolean {
        if (type != "movie" && type != "show") return false
        val key = if (type == "movie") "movies" else "shows"
        val deleted = response.optJSONObject("deleted") ?: return false
        if (deleted.optInt(key, -1) != 1) return false
        for (other in listOf("movies", "shows", "seasons", "episodes", "people")) {
            if (other != key && deleted.optInt(other, 0) != 0) return false
        }
        val missing = response.optJSONObject("not_found") ?: return false
        return missing.keys().asSequence().all { missing.optJSONArray(it)?.length() == 0 }
    }

    fun text(json: JSONObject, key: String): String? = json.optString(key).takeIf { it.isNotBlank() && it != "null" }
    fun items(array: JSONArray): List<TraktItem> = (0 until array.length()).mapNotNull { i ->
        val wrapper = array.optJSONObject(i) ?: return@mapNotNull null
        val type = wrapper.optString("type").ifBlank { if (wrapper.has("movie")) "movie" else if (wrapper.has("show")) "show" else "" }
        if (type !in listOf("movie", "show")) return@mapNotNull null
        val media = wrapper.optJSONObject(type) ?: return@mapNotNull null
        val ids = media.optJSONObject("ids") ?: return@mapNotNull null
        val id = ids.optLong("trakt")
        val title = text(media, "title") ?: return@mapNotNull null
        if (id <= 0) return@mapNotNull null
        TraktItem(type, id, title, media.optInt("year").takeIf { it > 0 }, text(media,"overview"),
            text(ids,"imdb")?.takeIf { it.matches(Regex("tt[0-9]+")) }, ids.optLong("tmdb").takeIf { it > 0 },
            media.optJSONObject("images")?.optJSONArray("poster")?.optString(0)?.let(::imageUrl),
            media.optDouble("rating",Double.NaN).takeIf { it.isFinite() && it in 0.0..10.0 })
    }.distinctBy { it.type to it.traktId }

    fun lists(array: JSONArray): List<TraktList> = (0 until array.length()).mapNotNull { i ->
        val json = array.optJSONObject(i) ?: return@mapNotNull null
        val id = json.optJSONObject("ids")?.optLong("trakt") ?: 0
        val name = text(json,"name") ?: return@mapNotNull null
        if (id > 0) TraktList(id,name) else null
    }.distinctBy { it.id }

    /** Only Trakt image hosts; never send user bearer tokens to image servers. */
    fun imageUrl(value: String): String? = runCatching {
        val uri = URI(if (value.startsWith("https://")) value else "https://$value")
        val host = uri.host?.lowercase().orEmpty()
        if (uri.scheme == "https" && (host == "trakt.tv" || host.endsWith(".trakt.tv")) && uri.userInfo == null && uri.port == -1)
            uri.toASCIIString() else null
    }.getOrNull()

    enum class Poll { SUCCESS, PENDING, SLOW_DOWN, STOP, RETRY }
    fun poll(status: Int) = when (status) { 200 -> Poll.SUCCESS;400 -> Poll.PENDING;429 -> Poll.SLOW_DOWN
        404,409,410,418,401,403 -> Poll.STOP;else -> Poll.RETRY }
}
