package dev.drewett.vista.data.music

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private class MusicHomeException(message:String):IllegalStateException(message)

/** Google OAuth/library plus user-authorized unofficial Music browsing; no media extraction. */
class MusicRepository private constructor(context: Context) {
    private val vault = MusicVault(context)
    private val mutex = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var credentials: MusicCredentials? = null
    private var polling: Job? = null
    private val _state = MutableStateFlow(MusicState())
    val state = _state.asStateFlow()

    init {
        scope.launch {
            mutex.withLock {
                credentials = try { vault.read() } catch (_: Exception) { null }
                val value = credentials
                _state.value = MusicState(loading=false,clientConfigured=value != null,authorized=value?.let { it.refreshToken.isNotEmpty() || it.accessToken.isNotEmpty() } == true,
                    channel=value?.takeIf { MusicProtocol.resourceId(it.channelId) }?.let { MusicChannel(it.channelId,it.channelTitle) })
                if(value != null && value.pendingPairing()!=null) {
                    _state.value=_state.value.copy(pairing=value.pendingPairing(),message="Resuming Google authorization for this code.")
                    startPolling(value)
                } else if(value?.pendingDeviceCode?.isNotEmpty()==true) {
                    value.clearPending()
                    runCatching { vault.write(value) }
                }
                ready.complete(Unit)
            }
        }
    }

    suspend fun saveClient(clientId: String, secret: String) = io {
        require(clientId.trim().matches(Regex("[A-Za-z0-9_.-]{10,256}\\.apps\\.googleusercontent\\.com"))) { "Enter a Google OAuth TV client ID." }
        require(secret.trim().length in 5..512 && secret.none { it.isISOControl() }) { "Enter the OAuth client secret." }
        polling?.cancel()
        val next = MusicCredentials(clientId.trim(),secret.trim())
        vault.write(next); credentials = next
        _state.value = MusicState(loading=false,clientConfigured=true,message="OAuth client saved. Choose Connect account.")
    }

    suspend fun startSignIn() = io {
        val value = credentials ?: throw IllegalStateException("Add an OAuth TV client first.")
        polling?.cancel()
        value.pendingPairing()?.let { pairing ->
            _state.value=_state.value.copy(pairing=pairing,message="Continue authorizing this code on your phone.")
            startPolling(value)
            return@io
        }
        val response = post("https://oauth2.googleapis.com/device/code",mapOf("client_id" to value.clientId,"scope" to "https://www.googleapis.com/auth/youtube.readonly"))
        val code = response.optString("device_code")
        val userCode = response.optString("user_code")
        val verify = response.optString("verification_url",response.optString("verification_uri"))
        require(code.length in 1..4096 && userCode.length in 1..30 && verify.startsWith("https://")) { "Google did not return a valid device code." }
        val expires = System.currentTimeMillis()+response.optLong("expires_in",900).coerceIn(60,3600)*1000
        val interval = response.optLong("interval",5).coerceIn(5,60)
        value.pendingDeviceCode=code;value.pendingUserCode=userCode;value.pendingVerificationUrl=verify
        value.pendingExpiresAt=expires;value.pendingInterval=interval
        vault.write(value)
        _state.value = _state.value.copy(loading=false,clientConfigured=true,pairing=MusicPairing(userCode,verify,expires),message="On your phone, authorize Blanktarget Records for this code.")
        startPolling(value)
    }

    /** Pending codes are encrypted so installing an update does not invalidate the user's screen. */
    private fun startPolling(value:MusicCredentials) {
        val code=value.pendingDeviceCode
        val expires=value.pendingExpiresAt
        polling = scope.launch {
            var seconds = value.pendingInterval.coerceIn(5,120)
            while (System.currentTimeMillis() < expires) {
                delay(seconds*1000)
                try {
                    val done = mutex.withLock {
                        if (credentials !== value) return@withLock true
                        val token = post("https://oauth2.googleapis.com/token",mapOf("client_id" to value.clientId,"client_secret" to value.clientSecret,
                            "device_code" to code,"grant_type" to "urn:ietf:params:oauth:grant-type:device_code"))
                        when (token.optString("error")) {
                            "authorization_pending" -> false
                            "slow_down" -> { seconds=(seconds+5).coerceAtMost(120);value.pendingInterval=seconds;vault.write(value);false }
                            "" -> {
                                acceptToken(value,token)
                                _state.value = _state.value.copy(authorized=true,pairing=null,message="Account connected. Checking its channel…")
                                true
                            }
                            else -> { value.clearPending();vault.write(value);_state.value = _state.value.copy(pairing=null,message="Google authorization was denied or expired. Connect again."); true }
                        }
                    }
                    if (done) {
                        if (_state.value.authorized) {
                            try { refreshLibrary() }
                            catch (cancelled:CancellationException) { throw cancelled }
                            catch (_:Exception) { mutex.withLock { _state.value=_state.value.copy(message="Account connected. Refresh the library when the network is available.") } }
                        }
                        return@launch
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutex.withLock { if(credentials===value) _state.value = _state.value.copy(message="Waiting for Google authorization. Check the network and finish approving this code.") } }
            }
            mutex.withLock {
                if(credentials===value) {
                    value.clearPending();runCatching { vault.write(value) }
                    _state.value = _state.value.copy(pairing=null,message="Pairing code expired. Connect account again.")
                }
            }
        }
    }

    suspend fun refreshLibrary() = io {
        val value = requireAuthorized()
        _state.value = _state.value.copy(loading=true,message=null)
        try {
            val channels = MusicProtocol.channels(api(value,"channels?part=snippet&mine=true&maxResults=50"))
            val selected = channels.firstOrNull { it.id == value.channelId } ?: channels.singleOrNull()
            _state.value = _state.value.copy(channels=channels,channel=selected,playlist=null,tracks=emptyList())
            if (selected != null) { loadPlaylists(value,selected); loadHome(value) }
            else _state.value = _state.value.copy(playlists=emptyList(),message=if (channels.isEmpty()) "No YouTube channel is available for this account." else "Choose your authorized channel.")
        } finally { _state.value = _state.value.copy(loading=false) }
    }

    suspend fun selectChannel(channel: MusicChannel) = io {
        val value = requireAuthorized()
        require(state.value.channels.any { it.id == channel.id })
        loadPlaylists(value,channel)
        loadHome(value)
    }

    private suspend fun loadHome(value:MusicCredentials) {
        try {
            var response=musicApi(value,"browse",JSONObject().put("browseId","FEmusic_home"))
            val shelves=MusicHomeProtocol.shelves(response).toMutableList()
            // Home commonly returns only three rows initially. Fetch a bounded set of extra rows.
            for(page in 0 until 2) {
                val continuation=MusicHomeProtocol.continuation(response) ?: break
                if(shelves.size>=12) break
                try {
                    response=musicApi(value,"browse",JSONObject().put("continuation",continuation))
                    shelves+=MusicHomeProtocol.shelves(response)
                } catch(cancelled:CancellationException) { throw cancelled }
                catch(_:Exception) { break }
            }
            _state.value=_state.value.copy(shelves=shelves.distinctBy { it.title },homeMessage=if(shelves.isEmpty()) "Music home is unavailable. Your playlists are still available." else null)
        } catch(cancelled:CancellationException) { throw cancelled }
        catch(error:MusicHomeException) { _state.value=_state.value.copy(homeMessage=error.message+" Your playlists are still available.") }
        catch(_:Exception) { _state.value=_state.value.copy(homeMessage="Music home could not refresh. Your playlists are still available.") }
    }

    suspend fun selectHomeCard(card:MusicHomeCard) = io {
        val value=requireAuthorized()
        require(state.value.shelves.any { card in it.cards })
        val response=when {
            card.playlistId!=null -> musicApi(value,"next",JSONObject().put("playlistId",card.playlistId).put("isAudioOnly",true))
            card.browseId!=null -> musicApi(value,"browse",JSONObject().put("browseId",card.browseId))
            card.videoId!=null -> musicApi(value,"next",JSONObject().put("videoId",card.videoId).put("isAudioOnly",true))
            else -> error("No Music destination.")
        }
        val tracks=MusicHomeProtocol.tracks(response)
        val nested=MusicHomeProtocol.shelves(response)
        _state.value=_state.value.copy(playlist=MusicPlaylist(card.playlistId ?: card.browseId ?: card.videoId.orEmpty(),card.title,card.thumbnail,tracks.size),
            tracks=tracks,shelves=if(tracks.isEmpty() && nested.isNotEmpty()) nested else state.value.shelves,
            message=if(tracks.isEmpty() && nested.isEmpty()) "This Music category is not available in the custom player yet." else null)
    }

    private suspend fun loadPlaylists(value: MusicCredentials,channel: MusicChannel) {
        val playlists = mutableListOf<MusicPlaylist>()
        var page = ""
        for (index in 0 until 4) {
            val response = api(value,"playlists?part=snippet,contentDetails&channelId=${encode(channel.id)}&maxResults=50"+if(page.isNotEmpty()) "&pageToken=${encode(page)}" else "")
            playlists += MusicProtocol.playlists(response)
            page = response.optString("nextPageToken")
            if (page.isEmpty()) break
        }
        value.channelId=channel.id; value.channelTitle=channel.title; vault.write(value)
        _state.value = _state.value.copy(channel=channel,playlists=playlists.distinctBy { it.id },playlist=null,tracks=emptyList(),shelves=emptyList(),homeMessage=null,
            message=if (playlists.isEmpty()) "No owned playlists were returned for this channel." else null)
    }

    suspend fun selectPlaylist(playlist: MusicPlaylist) = io {
        val value = requireAuthorized()
        require(state.value.playlists.any { it.id == playlist.id })
        val tracks = mutableListOf<MusicTrack>()
        var page = ""
        for (index in 0 until 20) {
            val response = api(value,"playlistItems?part=snippet&playlistId=${encode(playlist.id)}&maxResults=50"+if(page.isNotEmpty()) "&pageToken=${encode(page)}" else "")
            tracks += MusicProtocol.tracks(response)
            page=response.optString("nextPageToken")
            if(page.isEmpty()) break
        }
        _state.value = _state.value.copy(playlist=playlist,tracks=tracks,message=if(tracks.isEmpty()) "No playable playlist items were returned." else null)
    }

    suspend fun disconnect() = io {
        polling?.cancel()
        val previous=credentials
        vault.delete(); credentials=null
        _state.value=MusicState(loading=false,message="Disconnected on this TV.")
        val token=previous?.refreshToken?.ifBlank { previous.accessToken }
        if(!token.isNullOrBlank()) try { post("https://oauth2.googleapis.com/revoke",mapOf("token" to token)) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { /* Local tokens are removed even offline. */ }
    }

    private fun requireAuthorized() = credentials?.takeIf { it.refreshToken.isNotBlank() || it.accessToken.isNotBlank() }
        ?: throw IllegalStateException("Connect your Google account first.")
    private suspend fun ensureAccessToken(value:MusicCredentials) {
        if(value.accessToken.isBlank() || value.expiresAt < System.currentTimeMillis()+60_000) {
            val token=post("https://oauth2.googleapis.com/token",mapOf("client_id" to value.clientId,"client_secret" to value.clientSecret,
                "refresh_token" to value.refreshToken,"grant_type" to "refresh_token"))
            require(token.optString("error").isBlank()) { "Google authorization expired. Connect account again." }
            acceptToken(value,token)
        }
    }
    private suspend fun api(value: MusicCredentials,path:String):JSONObject {
        ensureAccessToken(value)
        val response=request("https://www.googleapis.com/youtube/v3/$path",token=value.accessToken)
        if(response.has("error")) throw IllegalStateException("YouTube could not load this library. Check API access and sign-in.")
        return response
    }
    private suspend fun musicApi(value:MusicCredentials,endpoint:String,body:JSONObject):JSONObject {
        ensureAccessToken(value)
        val version="1."+java.time.LocalDate.now(java.time.ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)+".01.00"
        body.put("context",JSONObject().put("client",JSONObject().put("clientName","WEB_REMIX").put("clientVersion",version).put("hl","en").put("gl","US")).put("user",JSONObject()))
        val response=request("https://music.youtube.com/youtubei/v1/$endpoint?alt=json&prettyPrint=false",body.toString(),value.accessToken,jsonBody=true)
        response.optJSONObject("error")?.let {
            throw MusicHomeException(if(it.optInt("code") in listOf(400,401,403)) "Music home needs a browser connection." else "Music home is temporarily unavailable.")
        }
        return response
    }
    private fun acceptToken(value:MusicCredentials,response:JSONObject) {
        val access=response.optString("access_token")
        require(access.length in 1..8192)
        val refresh=response.optString("refresh_token")
        value.accessToken=access
        if(refresh.isNotBlank()) value.refreshToken=refresh
        value.expiresAt=System.currentTimeMillis()+response.optLong("expires_in",3600).coerceIn(60,86400)*1000
        value.clearPending()
        vault.write(value)
    }
    private suspend fun post(url:String,fields:Map<String,String>)=request(url,body=fields.entries.joinToString("&") { encode(it.key)+"="+encode(it.value) })
    private suspend fun request(url:String,body:String?=null,token:String?=null,jsonBody:Boolean=false):JSONObject {
        currentCoroutineContext().ensureActive()
        val connection=URL(url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects=false; connection.connectTimeout=8_000; connection.readTimeout=10_000; connection.useCaches=false
            connection.setRequestProperty("Accept","application/json")
            if(token!=null) connection.setRequestProperty("Authorization","Bearer $token")
            if(jsonBody) {
                connection.setRequestProperty("Origin","https://music.youtube.com")
                connection.setRequestProperty("Cookie","SOCS=CAI")
                connection.setRequestProperty("X-Goog-Request-Time",(System.currentTimeMillis()/1000).toString())
                connection.setRequestProperty("User-Agent","Mozilla/5.0")
            }
            if(body!=null) {
                connection.requestMethod="POST"; connection.doOutput=true; connection.setRequestProperty("Content-Type",if(jsonBody) "application/json" else "application/x-www-form-urlencoded")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status=connection.responseCode
            require(status !in 300..399) { "Google redirected the API request unexpectedly." }
            val maxBytes=if(jsonBody) 8*1024*1024 else 2*1024*1024
            require(connection.contentLengthLong <= maxBytes)
            val input=if(status in 200..299) connection.inputStream else connection.errorStream ?: error("Google request failed")
            val output=ByteArrayOutputStream()
            input.use {
                val buffer=ByteArray(8192)
                while(true) {
                    currentCoroutineContext().ensureActive()
                    val count=it.read(buffer); if(count<0) break
                    require(output.size()+count <= maxBytes)
                    output.write(buffer,0,count)
                }
            }
            if(output.size()==0 && status in 200..299) return JSONObject()
            return JSONObject(output.toString(Charsets.UTF_8.name()))
        } finally { connection.disconnect() }
    }
    private fun encode(value:String)=URLEncoder.encode(value,Charsets.UTF_8.name())
    private suspend fun <T> io(block:suspend ()->T):T {
        ready.await()
        return withContext(Dispatchers.IO) { mutex.withLock {
            try { withTimeout(45_000) { block() } }
            catch (_:TimeoutCancellationException) { currentCoroutineContext().ensureActive();throw IllegalStateException("The music request timed out.") }
        } }
    }
    companion object {
        @Volatile private var instance:MusicRepository?=null
        fun get(context:Context):MusicRepository=instance ?: synchronized(this) { instance ?: MusicRepository(context.applicationContext).also { instance=it } }
    }
}
