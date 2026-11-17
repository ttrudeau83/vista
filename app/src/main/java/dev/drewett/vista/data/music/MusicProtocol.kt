package dev.drewett.vista.data.music

import org.json.JSONObject

data class MusicChannel(val id: String, val title: String)
data class MusicPlaylist(val id: String, val title: String, val thumbnail: String?, val count: Int)
data class MusicTrack(val id: String, val title: String, val thumbnail: String?)
data class MusicHomeCard(val title:String,val thumbnail:String?,val browseId:String?=null,val playlistId:String?=null,val videoId:String?=null)
data class MusicShelf(val title:String,val cards:List<MusicHomeCard>)
data class MusicPairing(val userCode: String, val verificationUrl: String, val expiresAt: Long)
data class MusicState(
    val loading: Boolean = true,
    val clientConfigured: Boolean = false,
    val authorized: Boolean = false,
    val channels: List<MusicChannel> = emptyList(),
    val channel: MusicChannel? = null,
    val playlists: List<MusicPlaylist> = emptyList(),
    val playlist: MusicPlaylist? = null,
    val tracks: List<MusicTrack> = emptyList(),
    val shelves: List<MusicShelf> = emptyList(),
    val artists: List<MusicHomeCard> = emptyList(),
    /** Recently played (Music history), newest first. */
    val history: List<MusicTrack> = emptyList(),
    /** Saved podcasts from the Music library. */
    val podcasts: List<MusicHomeCard> = emptyList(),
    val homeMessage: String? = null,
    val browserConnected:Boolean = false,
    val browserProfile:String? = null,
    val pairing: MusicPairing? = null,
    val message: String? = null,
)

object MusicProtocol {
    fun subscriptions(json:JSONObject):List<MusicHomeCard> = items(json).mapNotNull { item ->
        val snippet=item.optJSONObject("snippet") ?: return@mapNotNull null
        val id=snippet.optJSONObject("resourceId")?.optString("channelId").orEmpty()
        val title=snippet.optString("title")
        if(id.startsWith("UC") && resourceId(id) && title.isNotBlank()) MusicHomeCard(title.take(200),thumbnail(snippet),browseId=id) else null
    }.distinctBy { it.browseId }
    fun videoId(value: String): Boolean = value.matches(Regex("[A-Za-z0-9_-]{11}"))
    fun resourceId(value: String): Boolean = value.matches(Regex("[A-Za-z0-9_-]{1,128}"))
    fun channels(json: JSONObject): List<MusicChannel> = items(json).mapNotNull { item ->
        val id = item.optString("id")
        val title = item.optJSONObject("snippet")?.optString("title").orEmpty()
        if (resourceId(id) && title.isNotBlank()) MusicChannel(id,title.take(200)) else null
    }
    fun playlists(json: JSONObject): List<MusicPlaylist> = items(json).mapNotNull { item ->
        val id = item.optString("id")
        val snippet = item.optJSONObject("snippet") ?: return@mapNotNull null
        if (resourceId(id)) MusicPlaylist(id,snippet.optString("title").ifBlank { "Untitled playlist" }.take(200),
            thumbnail(snippet),item.optJSONObject("contentDetails")?.optInt("itemCount") ?: 0) else null
    }
    fun tracks(json: JSONObject): List<MusicTrack> = items(json).mapNotNull { item ->
        val snippet = item.optJSONObject("snippet") ?: return@mapNotNull null
        val id = snippet.optJSONObject("resourceId")?.optString("videoId").orEmpty()
        val title = snippet.optString("title")
        if (videoId(id) && title !in setOf("Deleted video","Private video")) MusicTrack(id,title.take(200),thumbnail(snippet)) else null
    }
    private fun thumbnail(snippet: JSONObject): String? {
        val values = snippet.optJSONObject("thumbnails") ?: return null
        val value = (values.optJSONObject("medium") ?: values.optJSONObject("default"))?.optString("url")
        return value?.takeIf { it.startsWith("https://") && it.length <= 2048 }
    }
    private fun items(json: JSONObject): List<JSONObject> {
        val values = json.optJSONArray("items") ?: return emptyList()
        return (0 until values.length()).mapNotNull { values.optJSONObject(it) }
    }
}
