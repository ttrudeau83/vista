package dev.drewett.vista.data.music

import org.json.JSONArray
import org.json.JSONObject

/** Tolerates optional/unknown web renderers; never interprets arbitrary URLs as playback. */
object MusicHomeProtocol {
    fun continuation(json:JSONObject):String? = nodes(json).mapNotNull {
        val section=it.optJSONObject("sectionListRenderer") ?: it.optJSONObject("sectionListContinuation") ?: return@mapNotNull null
        section.optJSONArray("continuations")?.optJSONObject(0)?.optJSONObject("nextContinuationData")?.optString("continuation")
            ?.takeIf { token -> token.length in 1..8192 && token.none(Char::isISOControl) }
    }.firstOrNull()
    fun shelves(json:JSONObject):List<MusicShelf> = nodes(json).mapNotNull { node ->
        val shelf=node.optJSONObject("musicCarouselShelfRenderer") ?: node.optJSONObject("musicShelfRenderer") ?: return@mapNotNull null
        val header=shelf.optJSONObject("header")
        val title=header?.let { nodes(it).mapNotNull { n -> n.optJSONObject("title")?.let(::text)?.takeIf(String::isNotBlank) }.firstOrNull() }
            ?: shelf.optJSONObject("title")?.let(::text) ?: return@mapNotNull null
        val cards=nodes(shelf.optJSONArray("contents")).mapNotNull(::card).distinctBy { listOf(it.browseId,it.playlistId,it.videoId) }.take(30).toList()
        cards.takeIf { it.isNotEmpty() }?.let { MusicShelf(title.take(200),it) }
    }.distinctBy { it.title }.take(16).toList()

    fun tracks(json:JSONObject):List<MusicTrack> = nodes(json).mapNotNull { node ->
        val renderer=node.optJSONObject("playlistPanelVideoRenderer") ?: node.optJSONObject("musicResponsiveListItemRenderer") ?: return@mapNotNull null
        val id=renderer.optString("videoId").ifBlank { renderer.optJSONObject("playlistItemData")?.optString("videoId").orEmpty() }
        if(!MusicProtocol.videoId(id)) return@mapNotNull null
        val title=renderer.optJSONObject("title")?.let(::text) ?: flexText(renderer)
        if(title.isBlank()) null else MusicTrack(id,title.take(200),thumbnail(renderer))
    }.distinctBy { it.id }.take(200).toList()

    private fun card(node:JSONObject):MusicHomeCard? {
        val renderer=node.optJSONObject("musicTwoRowItemRenderer") ?: node.optJSONObject("musicResponsiveListItemRenderer") ?: return null
        val title=renderer.optJSONObject("title")?.let(::text) ?: flexText(renderer)
        if(title.isBlank()) return null
        val endpoint=renderer.optJSONObject("navigationEndpoint") ?: nodes(renderer.optJSONObject("title")).mapNotNull { it.optJSONObject("navigationEndpoint") }.firstOrNull()
        val watch=endpoint?.optJSONObject("watchEndpoint") ?: nodes(renderer.optJSONObject("overlay")).mapNotNull { it.optJSONObject("watchEndpoint") }.firstOrNull()
        val browse=endpoint?.optJSONObject("browseEndpoint")?.optString("browseId")?.takeIf(MusicProtocol::resourceId)
        val playlist=watch?.optString("playlistId")?.takeIf(MusicProtocol::resourceId)
        val video=watch?.optString("videoId")?.takeIf(MusicProtocol::videoId)
            ?: renderer.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf(MusicProtocol::videoId)
        if(browse==null && playlist==null && video==null) return null
        return MusicHomeCard(title.take(200),thumbnail(renderer),browse,playlist,video)
    }
    private fun flexText(renderer:JSONObject):String = renderer.optJSONArray("flexColumns")?.optJSONObject(0)
        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")?.let(::text).orEmpty()
    private fun text(value:JSONObject):String = value.optString("simpleText").ifBlank {
        val runs=value.optJSONArray("runs") ?: return@ifBlank ""
        (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }
    }
    private fun thumbnail(value:JSONObject):String? = nodes(value.optJSONObject("thumbnail") ?: value.optJSONObject("thumbnailRenderer"))
        .mapNotNull { it.optString("url").takeIf { url -> url.startsWith("https://") && url.length<=2048 } }.lastOrNull()
    private fun nodes(value:Any?,depth:Int=0):Sequence<JSONObject> = sequence {
        if(depth>40) return@sequence
        when(value) {
            is JSONObject -> { yield(value); for(key in value.keys()) yieldAll(nodes(value.opt(key),depth+1)) }
            is JSONArray -> for(index in 0 until value.length()) yieldAll(nodes(value.opt(index),depth+1))
        }
    }
}
