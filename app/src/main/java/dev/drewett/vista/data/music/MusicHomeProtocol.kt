package dev.drewett.vista.data.music

import org.json.JSONArray
import org.json.JSONObject

/** Tolerates optional/unknown web renderers; never interprets arbitrary URLs as playback. */
object MusicHomeProtocol {
    fun cards(json:JSONObject):List<MusicHomeCard> = nodes(json).mapNotNull(::card)
        .distinctBy { listOf(it.browseId,it.playlistId,it.videoId) }.take(100).toList()
    /** A VL browse ID wraps a playlist; mixes must enter the playback queue, not artist browsing. */
    fun destination(card:MusicHomeCard):Pair<String,JSONObject> {
        if(card.browseId?.startsWith("UC")==true) return "browse" to JSONObject().put("browseId",card.browseId)
        val playlist=card.playlistId ?: card.browseId?.takeIf { it.startsWith("VL") }?.removePrefix("VL")?.takeIf(MusicProtocol::resourceId)
        if(playlist!=null || card.videoId!=null) {
            val body=JSONObject().put("enablePersistentPlaylistPanel",true).put("isAudioOnly",true).put("tunerSettingValue","AUTOMIX_SETTING_NORMAL")
            playlist?.let { body.put("playlistId",it) }
            card.videoId?.let { body.put("videoId",it) }
            return "next" to body
        }
        require(card.browseId!=null) { "No Music destination." }
        return "browse" to JSONObject().put("browseId",card.browseId)
    }
    fun continuation(json:JSONObject):String? = nodes(json).mapNotNull {
        val section=it.optJSONObject("sectionListRenderer") ?: it.optJSONObject("sectionListContinuation") ?: return@mapNotNull null
        section.optJSONArray("continuations")?.optJSONObject(0)?.optJSONObject("nextContinuationData")?.optString("continuation")
            ?.takeIf { token -> token.length in 1..8192 && token.none(Char::isISOControl) }
    }.firstOrNull()
    fun shelves(json:JSONObject):List<MusicShelf> = nodes(json).mapNotNull { node ->
        val shelf=node.optJSONObject("musicCarouselShelfRenderer") ?: node.optJSONObject("musicShelfRenderer") ?: return@mapNotNull null
        val header=shelf.optJSONObject("header")
        val title=header?.let { nodes(it).mapNotNull { n -> sequenceOf(n.optJSONObject("title"),n.optJSONObject("strapline"))
            .mapNotNull { value -> value?.let(::text)?.takeIf(String::isNotBlank) }.firstOrNull() }.firstOrNull() }
            ?: shelf.optJSONObject("title")?.let(::text) ?: return@mapNotNull null
        val cards=nodes(shelf.optJSONArray("contents")).mapNotNull(::card).distinctBy { listOf(it.browseId,it.playlistId,it.videoId) }.take(30).toList()
        cards.takeIf { it.isNotEmpty() }?.let { MusicShelf(title.take(200),it) }
    }.distinctBy { it.title }.take(16).toList()

    fun tracks(json:JSONObject):List<MusicTrack> = nodes(json,skipCounterparts=true).mapNotNull { node ->
        // musicMultiRowListItemRenderer = podcast episode rows (video ID under onTap).
        val renderer=node.optJSONObject("playlistPanelVideoRenderer") ?: node.optJSONObject("musicResponsiveListItemRenderer")
            ?: node.optJSONObject("musicMultiRowListItemRenderer") ?: return@mapNotNull null
        if(renderer.has("unplayableText") || (renderer.has("isPlayable") && !renderer.optBoolean("isPlayable"))) return@mapNotNull null
        val id=renderer.optString("videoId").ifBlank { renderer.optJSONObject("playlistItemData")?.optString("videoId").orEmpty() }
            .ifBlank { firstColumnEndpoint(renderer)?.optJSONObject("watchEndpoint")?.optString("videoId").orEmpty() }
            .ifBlank { renderer.optJSONObject("onTap")?.optJSONObject("watchEndpoint")?.optString("videoId").orEmpty() }
        if(!MusicProtocol.videoId(id)) return@mapNotNull null
        val title=renderer.optJSONObject("title")?.let(::text) ?: flexText(renderer)
        if(title.isBlank()) null else MusicTrack(id,title.take(200),thumbnail(renderer))
    }.distinctBy { it.id }.take(200).toList()

    /** Saved podcasts (library grid). Only real podcast pages (MPSP…) — skips the "Add podcast" tile. */
    fun podcasts(json:JSONObject):List<MusicHomeCard> = cards(json).filter { it.browseId?.startsWith("MPSP")==true }.take(40)

    /** Diagnostics: the renderer type names present (structure only, no content). */
    fun rendererNames(json:JSONObject):String = nodes(json).flatMap { it.keys().asSequence() }
        .filter { it.endsWith("Renderer") }.distinct().take(40).joinToString(",") +
        // YouTube Music's own UI message (e.g. "sign in" / "history is paused"), not user content.
        nodes(json).mapNotNull { it.optJSONObject("messageRenderer") }.mapNotNull { m ->
            sequenceOf(m.optJSONObject("text"),m.optJSONObject("subtext")).mapNotNull { t -> t?.let(::text) }.joinToString(" / ").takeIf(String::isNotBlank)
        }.firstOrNull()?.let { " | message: ${it.take(160)}" }.orEmpty()

    private fun card(node:JSONObject):MusicHomeCard? {
        val renderer=node.optJSONObject("musicTwoRowItemRenderer") ?: node.optJSONObject("musicResponsiveListItemRenderer") ?: return null
        val title=renderer.optJSONObject("title")?.let(::text) ?: flexText(renderer)
        if(title.isBlank()) return null
        val endpoint=renderer.optJSONObject("navigationEndpoint") ?: nodes(renderer.optJSONObject("title")).mapNotNull { it.optJSONObject("navigationEndpoint") }.firstOrNull()
            ?: firstColumnEndpoint(renderer)
        val watch=endpoint?.optJSONObject("watchEndpoint") ?: endpoint?.optJSONObject("watchPlaylistEndpoint")
            ?: sequenceOf(renderer.optJSONObject("overlay"),renderer.optJSONObject("thumbnailOverlay")).flatMap { nodes(it) }
                .mapNotNull { it.optJSONObject("watchEndpoint") ?: it.optJSONObject("watchPlaylistEndpoint") }.firstOrNull()
        val browse=endpoint?.optJSONObject("browseEndpoint")?.optString("browseId")?.takeIf(MusicProtocol::resourceId)
        val playlist=watch?.optString("playlistId")?.takeIf(MusicProtocol::resourceId)
        val video=watch?.optString("videoId")?.takeIf(MusicProtocol::videoId)
            ?: renderer.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf(MusicProtocol::videoId)
        if(browse==null && playlist==null && video==null) return null
        return MusicHomeCard(title.take(200),thumbnail(renderer),browse,playlist,video)
    }
    private fun flexText(renderer:JSONObject):String = renderer.optJSONArray("flexColumns")?.optJSONObject(0)
        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")?.let(::text).orEmpty()
    private fun firstColumnEndpoint(renderer:JSONObject):JSONObject? = nodes(renderer.optJSONArray("flexColumns")?.optJSONObject(0))
        .mapNotNull { it.optJSONObject("navigationEndpoint") }.firstOrNull()
    private fun text(value:JSONObject):String = value.optString("simpleText").ifBlank {
        val runs=value.optJSONArray("runs") ?: return@ifBlank ""
        (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }
    }
    private fun thumbnail(value:JSONObject):String? = nodes(value.optJSONObject("thumbnail") ?: value.optJSONObject("thumbnailRenderer"))
        .mapNotNull { it.optString("url").takeIf { url -> url.startsWith("https://") && url.length<=2048 } }.lastOrNull()
    private fun nodes(value:Any?,depth:Int=0,skipCounterparts:Boolean=false):Sequence<JSONObject> = sequence {
        if(depth>40) return@sequence
        when(value) {
            is JSONObject -> { yield(value); for(key in value.keys()) if(!skipCounterparts || key!="counterpart") yieldAll(nodes(value.opt(key),depth+1,skipCounterparts)) }
            is JSONArray -> for(index in 0 until value.length()) yieldAll(nodes(value.opt(index),depth+1,skipCounterparts))
        }
    }
}
