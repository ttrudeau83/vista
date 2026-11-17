package dev.drewett.vista.data.trakt

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Trakt lists with explicit single-item removal; credentials and cached metadata stay encrypted. */
class TraktRepository private constructor(context: Context) {
    private val vault=TraktVault(context.applicationContext)
    private val posters=File(context.applicationContext.cacheDir,"trakt-posters").apply { mkdirs() }
    private val posterLock=Any()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val mutex=Mutex()
    private val ready=CompletableDeferred<Unit>()
    private var stored=JSONObject()
    private var polling:Job?=null
    private var lastRefreshAt=0L
    private var nextLibraryRequestAt=0L
    private val mutable=MutableStateFlow(TraktState(loading=true))
    val state=mutable.asStateFlow()
    init { scope.launch {
        try { mutex.withLock {
            stored=runCatching { vault.read() }.getOrNull() ?: JSONObject()
            mutable.value=TraktState(clientConfigured=stored.optString("clientId").isNotBlank(),authorized=stored.optString("refreshToken").isNotBlank(),
                items=readItems(stored.optJSONArray("items") ?: JSONArray()),lists=TraktProtocol.lists(stored.optJSONArray("lists") ?: JSONArray()),
                selectedListId=stored.optLong("selectedListId").takeIf { it > 0 })
        } } finally { ready.complete(Unit) }
        if(mutable.value.authorized) runCatching { refreshLibrary() }
    } }

    suspend fun saveClient(clientId:String, secret:String="") = io {
        require(clientId.trim().matches(Regex("[A-Za-z0-9_-]{16,256}"))) { "Enter your Trakt application's client ID." }
        require(secret.length<=512 && secret.none { it.isISOControl() }) { "Invalid client secret." }
        polling?.cancel()
        val next=JSONObject().put("clientId",clientId.trim()).put("clientSecret",secret.trim())
        vault.write(next);stored=next;lastRefreshAt=0;nextLibraryRequestAt=0
        clearPosters()
        mutable.value=TraktState(clientConfigured=true,message="Client saved. Choose Connect Trakt.")
    }

    suspend fun startSignIn() = io {
        check(stored.optString("clientId").isNotBlank()) { "Add your Trakt client ID first." }
        polling?.cancel()
        val response=request("/oauth/device/code",JSONObject().put("client_id",stored.getString("clientId")))
        currentCoroutineContext().ensureActive()
        check(response.status==200) { response.message() }
        val body=JSONObject(response.body)
        val code=body.optString("device_code")
        val userCode=body.optString("user_code")
        val verification=body.optString("verification_url")
        require(code.isNotBlank() && userCode.length in 1..32 && verification.matches(Regex("https://(?:[a-z]+\\.)?trakt\\.tv/.*"))) { "Trakt returned invalid pairing information." }
        val expires=System.currentTimeMillis()+body.optLong("expires_in",600).coerceIn(30,3600)*1000
        val interval=body.optLong("interval",5).coerceIn(1,120)
        mutable.value=mutable.value.copy(pairing=TraktPairing(userCode,verification,expires),message="Approve this code on your phone.")
        polling=scope.launch { poll(code,expires,interval) }
    }

    private suspend fun poll(code:String, expires:Long, initialInterval:Long) {
        var interval=initialInterval
        while(System.currentTimeMillis()<expires) {
            delay(interval*1000)
            if(System.currentTimeMillis()>=expires) break
            var done=false
            var connected=false
            try { mutex.withLock {
                val body=authBody().put("code",code)
                val response=request("/oauth/device/token",body)
                currentCoroutineContext().ensureActive()
                when(TraktProtocol.poll(response.status)) {
                    TraktProtocol.Poll.SUCCESS -> { acceptToken(JSONObject(response.body));mutable.value=mutable.value.copy(authorized=true,pairing=null,message="Trakt connected.");done=true;connected=true }
                    TraktProtocol.Poll.PENDING -> Unit
                    TraktProtocol.Poll.SLOW_DOWN -> interval=maxOf(interval+5,response.retryAfter).coerceAtMost(300)
                    TraktProtocol.Poll.STOP -> { mutable.value=mutable.value.copy(pairing=null,message="Trakt authorization was denied, expired, or unavailable. Connect again.");done=true }
                    TraktProtocol.Poll.RETRY -> { interval=maxOf(interval,30);mutable.value=mutable.value.copy(message="Waiting for Trakt. Check your connection.") }
                }
            } } catch(e:CancellationException) { throw e } catch(_:Exception) { interval=maxOf(interval,30) }
            if(done) {
                if(connected) try { refreshLibrary() } catch(e:CancellationException) { throw e } catch(_:Exception) { /* State carries a sanitized refresh message. */ }
                return
            }
        }
        mutex.withLock { mutable.value=mutable.value.copy(pairing=null,message="Trakt code expired. Connect again.") }
    }

    fun cancelSignIn() {
        polling?.cancel();polling=null
        if(mutable.value.pairing!=null) mutable.value=mutable.value.copy(pairing=null,message="Sign-in cancelled.")
    }

    suspend fun disconnect() = io {
        polling?.cancel();polling=null
        // Removing local authorization always works offline. The user can revoke this app on Trakt.
        vault.delete();stored=JSONObject();clearPosters();lastRefreshAt=0;nextLibraryRequestAt=0
        mutable.value=TraktState(message="Trakt disconnected. Local authorization and saved lists removed.")
    }

    suspend fun refreshLibrary(force:Boolean=false) = io {
        if(mutable.value.authorized && (force || System.currentTimeMillis()-lastRefreshAt>300_000)) refreshLocked()
    }
    suspend fun selectList(id:Long?) = io {
        require(id==null || mutable.value.lists.any { it.id==id }) { "Choose an available Trakt list." }
        val previous=mutable.value.selectedListId
        mutable.value=mutable.value.copy(selectedListId=id)
        try { refreshLocked() } catch(e:Exception) { mutable.value=mutable.value.copy(selectedListId=previous);throw e }
    }

    /** The UI captures the list identity when its menu opens; never remove from a later selection. */
    suspend fun removeItem(type: String, traktId: Long, expectedListId: Long?) = io {
        check(mutable.value.authorized) { "Connect Trakt first." }
        check(mutable.value.selectedListId == expectedListId) { "The selected list changed." }
        check(expectedListId == null || mutable.value.lists.any { it.id == expectedListId }) { "The selected list is unavailable." }
        val body = TraktProtocol.removalBody(type, traktId)
        check(mutable.value.items.any { it.type == type && it.traktId == traktId }) { "Refresh this list first." }
        check(System.currentTimeMillis() >= nextLibraryRequestAt) { "Trakt requested a pause." }
        mutable.value = mutable.value.copy(loading = true, message = null)
        var removedOnTrakt = false
        try {
            val path = TraktProtocol.removalPath(expectedListId)
            currentCoroutineContext().ensureActive()
            var response = request(path, body, token())
            currentCoroutineContext().ensureActive()
            if (response.status == 401) {
                val refreshed = token(force = true)
                currentCoroutineContext().ensureActive()
                response = request(path, body, refreshed)
            }
            if (response.status == 429) nextLibraryRequestAt = System.currentTimeMillis() + response.retryAfter * 1000
            check(response.status == 200)
            check(TraktProtocol.removalConfirmed(type, JSONObject(response.body)))
            removedOnTrakt = true
            val remaining = mutable.value.items.filterNot { it.type == type && it.traktId == traktId }
            val next = JSONObject(stored.toString()).put("items", writeItems(remaining))
            vault.write(next)
            stored = next
            lastRefreshAt = 0 // The next refresh also reconciles edits made by another Trakt client.
            mutable.value = mutable.value.copy(items = remaining, message = "Removed from this Trakt list.")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            lastRefreshAt = 0
            mutable.value = mutable.value.copy(message = when {
                removedOnTrakt -> "Removed on Trakt, but the saved list could not update. Refresh this list."
                !mutable.value.authorized -> "Trakt authorization expired. Connect again."
                System.currentTimeMillis() < nextLibraryRequestAt -> "Trakt requested a pause. Try removing the title later."
                else -> "Trakt could not confirm removal. Your displayed list is unchanged; refresh and try again."
            })
            throw IllegalStateException("Trakt removal could not complete.")
        } finally {
            mutable.value = mutable.value.copy(loading = false)
        }
    }

    private suspend fun refreshLocked() {
        check(mutable.value.authorized) { "Connect your Trakt account first." }
        check(System.currentTimeMillis()>=nextLibraryRequestAt) { "Trakt requested a pause. Try again shortly." }
        mutable.value=mutable.value.copy(loading=true,message=null)
        try {
            val lists=pages("/users/me/lists")
            val available=TraktProtocol.lists(lists)
            val selected=mutable.value.selectedListId?.takeIf { chosen -> available.any { it.id==chosen } }
            val raw=if(selected==null) pages("/sync/watchlist?extended=full") else pages("/users/me/lists/$selected/items?extended=full")
            prunePosters(mutable.value.items.mapNotNull { it.posterUrl }.toSet())
            val items=coroutineScope {
                val permits=Semaphore(4)
                TraktProtocol.items(raw).map { item -> async(Dispatchers.IO) { permits.withPermit {
                    currentCoroutineContext().ensureActive()
                    val path=item.posterUrl?.let { cachePoster(it) }
                    currentCoroutineContext().ensureActive()
                    item.copy(posterUrl=path)
                } } }.awaitAll()
            }
            currentCoroutineContext().ensureActive()
            prunePosters(items.mapNotNull { it.posterUrl }.toSet())
            val next=JSONObject(stored.toString()).put("lists",lists).put("selectedListId",selected).put("items",writeItems(items))
            vault.write(next);stored=next
            lastRefreshAt=System.currentTimeMillis()
            mutable.value=mutable.value.copy(items=items,lists=available,selectedListId=selected,loading=false,message=if(items.isEmpty()) "This Trakt list has no movies or shows." else null)
        } catch(e:CancellationException) { throw e }
        catch(e:Exception) {
            val message=when {
                !mutable.value.authorized -> "Trakt authorization expired. Connect again."
                System.currentTimeMillis()<nextLibraryRequestAt -> "Trakt requested a pause. Try again later; showing the last saved list."
                else -> "Could not refresh Trakt. Check the connection and try again; showing the last saved list."
            }
            mutable.value=mutable.value.copy(message=message)
            // Do not forward JSON parser/network messages that may contain response fragments.
            throw IllegalStateException(message)
        }
        finally { mutable.value=mutable.value.copy(loading=false) }
    }

    private suspend fun pages(path:String):JSONArray {
        val output=JSONArray()
        var page=1
        while(page<=100) {
            currentCoroutineContext().ensureActive()
            val url=path+(if('?' in path) "&" else "?")+"page=$page&limit=100"
            var response=request(url,token=token())
            if(response.status==401) response=request(url,token=token(force=true))
            if(response.status==429) nextLibraryRequestAt=System.currentTimeMillis()+response.retryAfter*1000
            check(response.status==200) { response.message() }
            val rows=JSONArray(response.body)
            for(i in 0 until rows.length()) output.put(rows.get(i))
            val count=response.pageCount
            if(rows.length()==0 || (count!=null && page>=count) || (count==null && rows.length()<100)) return output
            page++
        }
        error("This Trakt list is too large to load completely.")
    }

    private fun authBody()=JSONObject().put("client_id",stored.getString("clientId")).apply {
        stored.optString("clientSecret").takeIf { it.isNotBlank() }?.let { put("client_secret",it) }
    }
    private fun acceptToken(json:JSONObject) {
        val access=json.optString("access_token");val refresh=json.optString("refresh_token")
        require(access.isNotBlank() && refresh.isNotBlank()) { "Trakt returned invalid authorization." }
        stored.put("accessToken",access).put("refreshToken",refresh)
            .put("expiresAt",System.currentTimeMillis()+json.optLong("expires_in",604800).coerceIn(60,31_536_000)*1000)
        vault.write(stored)
    }
    private fun token(force:Boolean=false):String {
        if(!force && stored.optString("accessToken").isNotBlank() && stored.optLong("expiresAt")>System.currentTimeMillis()+60_000) return stored.getString("accessToken")
        val response=request("/oauth/token",authBody().put("refresh_token",stored.optString("refreshToken")).put("grant_type","refresh_token"))
        if(response.status in listOf(400,401,403)) {
            stored.remove("accessToken");stored.remove("refreshToken");stored.remove("items");vault.write(stored)
            mutable.value=mutable.value.copy(authorized=false,items=emptyList(),message="Trakt authorization expired. Connect again.")
            error("Trakt authorization expired. Connect again.")
        }
        check(response.status==200) { response.message() }
        acceptToken(JSONObject(response.body));return stored.getString("accessToken")
    }

    private data class Response(val status:Int,val body:String,val pageCount:Int?,val retryAfter:Long) {
        fun message()=when(status) { 401 -> "Reconnect Trakt to refresh this list.";403 -> "Trakt rejected this application. Check its client ID and account access.";429 -> "Trakt rate limit reached. Retry after $retryAfter seconds.";else -> "Trakt request failed ($status). Showing the last saved list." }
    }
    private fun request(path:String,body:JSONObject?=null,token:String?=null):Response {
        val connection=URL("https://api.trakt.tv$path").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout=15_000;connection.readTimeout=20_000;connection.instanceFollowRedirects=false
            connection.setRequestProperty("Content-Type","application/json");connection.setRequestProperty("trakt-api-version","2")
            connection.setRequestProperty("trakt-api-key",stored.optString("clientId"));connection.setRequestProperty("User-Agent","Vista/0.4 TraktLibrary")
            token?.let { connection.setRequestProperty("Authorization","Bearer $it") }
            if(body!=null) { connection.requestMethod="POST";connection.doOutput=true;connection.outputStream.use { it.write(body.toString().toByteArray()) } }
            val status=connection.responseCode
            val stream=if(status in 200..299) connection.inputStream else connection.errorStream
            val bytes=stream?.use { boundedBytes(it,4_194_304) } ?: byteArrayOf()
            require(bytes.size<=4_194_304) { "Trakt response is too large." }
            return Response(status,bytes.toString(Charsets.UTF_8),connection.getHeaderField("X-Pagination-Page-Count")?.toIntOrNull(),connection.getHeaderField("Retry-After")?.toLongOrNull()?.coerceIn(1,3600) ?: 30)
        } finally { connection.disconnect() }
    }

    private fun cachePoster(url:String):String? {
        val safe=TraktProtocol.imageUrl(url) ?: return null
        val name=MessageDigest.getInstance("SHA-256").digest(safe.toByteArray()).joinToString("") { "%02x".format(it) }
        val file=File(posters,name)
        if(file.isFile && file.length()>0) { file.setLastModified(System.currentTimeMillis());return file.absolutePath }
        // Keep room for a maximum-sized download and skip extra network work when full.
        if((posters.listFiles()?.sumOf { it.length() } ?: 0L)>62L*1024*1024) return null
        return runCatching {
            val connection=URL(safe).openConnection() as HttpURLConnection
            val bytes=try {
                connection.instanceFollowRedirects=false;connection.connectTimeout=5_000;connection.readTimeout=5_000
                check(connection.responseCode==200 && connection.contentType?.startsWith("image/")==true)
                connection.inputStream.use { boundedBytes(it,2_097_152) }
            } finally { connection.disconnect() }
            check(bytes.size in 1..2_097_152)
            synchronized(posterLock) {
                if(file.isFile && file.length()>0) return file.absolutePath
                if((posters.listFiles()?.sumOf { it.length() } ?: 0L)+bytes.size>64L*1024*1024) return null
                val atomic=android.util.AtomicFile(file);val output=atomic.startWrite()
                try { output.write(bytes);atomic.finishWrite(output) } catch(e:Exception) { atomic.failWrite(output);throw e }
            }
            file.absolutePath
        }.getOrElse { if(it is CancellationException) throw it;null }
    }
    private fun boundedBytes(input:java.io.InputStream,limit:Int):ByteArray {
        val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true) { val count=input.read(buffer);if(count<0) break;require(output.size()+count<=limit) { "Response is too large." };output.write(buffer,0,count) }
        return output.toByteArray()
    }
    private fun clearPosters() { posters.listFiles()?.forEach { it.delete() } }
    private fun prunePosters(active:Set<String>) {
        val files=posters.listFiles()?.toList().orEmpty()
        var size=files.sumOf { it.length() }
        files.filter { it.absolutePath !in active }.sortedBy { it.lastModified() }.forEach { file ->
            if(size>48L*1024*1024) { val length=file.length();if(file.delete()) size-=length }
        }
    }
    private fun writeItems(items:List<TraktItem>)=JSONArray().apply { items.forEach { item -> put(JSONObject()
        .put("type",item.type).put("traktId",item.traktId).put("title",item.title).put("year",item.year).put("overview",item.overview)
        .put("imdbId",item.imdbId).put("tmdbId",item.tmdbId).put("posterUrl",item.posterUrl).put("rating",item.rating)) } }
    private fun readItems(array:JSONArray)= (0 until array.length()).mapNotNull { i -> runCatching {
        val j=array.getJSONObject(i)
        TraktItem(j.getString("type"),j.getLong("traktId"),j.getString("title"),j.optInt("year").takeIf { it>0 },TraktProtocol.text(j,"overview"),
            TraktProtocol.text(j,"imdbId"),j.optLong("tmdbId").takeIf { it>0 },TraktProtocol.text(j,"posterUrl")?.takeIf { path -> File(path).parentFile==posters && File(path).isFile },j.optDouble("rating",Double.NaN).takeIf { it.isFinite() })
    }.getOrNull() }
    private suspend fun <T> io(block:suspend ()->T):T {
        ready.await()
        return withContext(Dispatchers.IO) { mutex.withLock {
            try { block() } catch(e:CancellationException) { throw e }
            catch(_:Exception) { throw IllegalStateException("Could not complete the Trakt request. Check settings and connection.") }
        } }
    }
    companion object {
        @Volatile private var instance:TraktRepository?=null
        fun get(context:Context):TraktRepository=instance ?: synchronized(this) { instance ?: TraktRepository(context.applicationContext).also { instance=it } }
    }
}
