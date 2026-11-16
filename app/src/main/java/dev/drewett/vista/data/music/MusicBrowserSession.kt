package dev.drewett.vista.data.music

import org.json.JSONObject
import java.security.MessageDigest

/** Session material is never a data class, UI state, URL parameter or log value. */
internal class MusicBrowserSession private constructor(
    val cookie:String,
    val authUser:String,
    val visitorId:String?,
    val pageId:String?,
) {
    override fun toString()="MusicBrowserSession(redacted)"

    fun headers(nowSeconds:Long=System.currentTimeMillis()/1000):Map<String,String> {
        val cookies=cookies(cookie)
        val sid=cookies["__Secure-3PAPISID"]?.takeIf(String::isNotBlank) ?: cookies.getValue("SAPISID")
        val hash=MessageDigest.getInstance("SHA-1").digest("$nowSeconds $sid $ORIGIN".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        return buildMap {
            put("Cookie",cookie);put("Authorization","SAPISIDHASH ${nowSeconds}_$hash")
            put("X-Goog-AuthUser",authUser);put("Origin",ORIGIN)
            visitorId?.let { put("X-Goog-Visitor-Id",it) }
            pageId?.let { put("X-Goog-PageId",it) }
        }
    }

    fun toJson():JSONObject=JSONObject().put("cookie",cookie).put("authUser",authUser)
        .put("visitorId",visitorId).put("pageId",pageId)

    companion object {
        const val ORIGIN="https://music.youtube.com"
        private val retained=setOf("cookie","x-goog-authuser","x-goog-visitor-id","x-goog-pageid","origin","x-origin")
        fun parse(raw:String):MusicBrowserSession {
            require(raw.toByteArray(Charsets.UTF_8).size<=24*1024 && raw.none { it=='\u0000' }) { "Request headers are too large or invalid." }
            val headers=mutableMapOf<String,String>()
            fun add(name:String,value:String) {
                val key=name.trim().lowercase(java.util.Locale.ROOT)
                if(key !in retained) return
                require(value.all { it.code in 32..126 }) { "A request header contains invalid characters." }
                require(headers[key]==null || headers[key]==value.trim()) { "A request header is duplicated." }
                headers[key]=value.trim()
            }
            if(raw.trimStart().startsWith("{")) {
                val json=JSONObject(raw)
                for(key in json.keys()) { require(json.opt(key) is String) { "Use a JSON object of request header strings." };add(key,json.getString(key)) }
            } else {
                val lines=raw.lineSequence().map(String::trim).toList()
                for((index,line) in lines.withIndex()) {
                    if(line.startsWith(":authority:",ignoreCase=true)) require(line.substringAfter(":authority:").trim()=="music.youtube.com") { "Copy headers from YouTube Music only." }
                    val separator=line.indexOf(':')
                    if(separator>0) add(line.substring(0,separator),line.substring(separator+1).trim().ifBlank { lines.getOrNull(index+1).orEmpty() })
                    else if(line.lowercase(java.util.Locale.ROOT) in retained && index+1<lines.size) add(line,lines[index+1])
                }
            }
            for(key in listOf("origin","x-origin")) headers[key]?.let {
                require(it.removeSuffix("/")==ORIGIN) { "Copy headers from YouTube Music only." }
            }
            val cookie=requireNotNull(headers["cookie"]) { "The copied request needs its Cookie header." }
            val cookies=cookies(cookie)
            require(!cookies["__Secure-3PAPISID"].isNullOrBlank() || !cookies["SAPISID"].isNullOrBlank()) { "The request does not contain a signed-in Music session." }
            val authUser=headers["x-goog-authuser"] ?: "0"
            require(authUser.matches(Regex("[0-9]"))) { "The selected Google account index is invalid." }
            fun optional(key:String,max:Int):String? = headers[key]?.takeIf(String::isNotBlank)?.also {
                require(it.length<=max && it.matches(Regex("[A-Za-z0-9_=%+./-]+"))) { "A Music session header is invalid." }
            }
            return MusicBrowserSession(cookie,authUser,optional("x-goog-visitor-id",4096),optional("x-goog-pageid",128))
        }
        fun fromJson(json:JSONObject):MusicBrowserSession = parse(JSONObject().put("cookie",json.getString("cookie"))
            .put("x-goog-authuser",json.optString("authUser","0"))
            .apply { for((source,target) in listOf("visitorId" to "x-goog-visitor-id","pageId" to "x-goog-pageid")) {
                json.optString(source).takeIf { it.isNotBlank() && it!="null" }?.let { put(target,it) }
            } }.toString())
        fun profile(response:JSONObject):String? {
            fun text(value:JSONObject?):String = value?.optString("simpleText").orEmpty().ifBlank {
                val runs=value?.optJSONArray("runs")
                if(runs==null) "" else (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }
            }
            fun header(value:Any?,depth:Int=0):JSONObject? {
                if(depth>30) return null
                if(value is JSONObject) {
                    value.optJSONObject("activeAccountHeaderRenderer")?.let { return it }
                    for(key in value.keys()) header(value.opt(key),depth+1)?.let { return it }
                } else if(value is org.json.JSONArray) for(index in 0 until value.length()) header(value.opt(index),depth+1)?.let { return it }
                return null
            }
            val active=header(response) ?: return null
            val name=text(active.optJSONObject("accountName")).take(200)
            val handle=text(active.optJSONObject("channelHandle")).take(100)
            if(name.isBlank() || name.any(Char::isISOControl) || handle.any(Char::isISOControl)) return null
            return listOf(name,handle).filter(String::isNotBlank).distinct().joinToString(" · ")
        }
        fun matchesChannel(profile:String,channelTitle:String):Boolean {
            fun normalize(value:String)=value.lowercase(java.util.Locale.ROOT).filter(Char::isLetterOrDigit)
            val expected=normalize(channelTitle)
            return expected.isNotEmpty() && normalize(profile.substringBefore(" · "))==expected
        }
        private fun cookies(value:String):Map<String,String> = buildMap {
            for(part in value.split(';')) {
                if(part.isBlank()) continue
                val separator=part.indexOf('=')
                require(separator>0) { "The Cookie header is invalid." }
                val name=part.substring(0,separator).trim();val content=part.substring(separator+1).trim()
                require(name.matches(Regex("[A-Za-z0-9_!#$%&'*+.^`|~-]+")) && name !in this) { "The Cookie header is invalid." }
                require(content.none { it=='\r' || it=='\n' || it=='\u0000' }) { "The Cookie header is invalid." }
                put(name,content)
            }
        }
    }
}
