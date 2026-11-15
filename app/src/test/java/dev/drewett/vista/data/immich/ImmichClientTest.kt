package dev.drewett.vista.data.immich

import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmichClientTest {
    private val albumId = "11111111-1111-4111-8111-111111111111"
    private val imageId = "22222222-2222-4222-8222-222222222222"

    @Test
    fun crossOriginRedirectNeverReceivesTheApiKey() = runBlocking {
        val redirectedRequests = AtomicInteger()
        withServer { destination ->
            destination.route("/stolen") { redirectedRequests.incrementAndGet(); Response(body = "[]") }
            withServer { origin ->
                origin.route("/api/albums") { Response(302, headers = mapOf("Location" to "${destination.origin}/stolen")) }
                val failure = failure { ImmichClient("${origin.origin}/api", "test-only-key").albums() }
                assertTrue(failure is ImmichException)
                assertEquals(0, redirectedRequests.get())
            }
        }
    }

    @Test
    fun sameOriginRedirectPreservesAuthenticationAndLoadsAlbums() = runBlocking {
        withServer { server ->
            server.route("/api/albums") { Response(307, headers = mapOf("Location" to "/api/final-albums")) }
            server.route("/api/final-albums") { request ->
                if (request.headers["x-api-key"] == "test-only-key") Response(body = """[{"id":"$albumId","albumName":"Family"}]""")
                else Response(401)
            }
            assertEquals("Family", ImmichClient("${server.origin}/api", "test-only-key").albums().single().name)
        }
    }

    @Test
    fun authenticationFailureDoesNotExposeResponseBodyOrKey() = runBlocking {
        withServer { server ->
            server.route("/api/albums") { Response(401, "private-server-diagnostic test-only-key") }
            val failure = failure { ImmichClient("${server.origin}/api", "test-only-key").albums() }
            assertTrue(failure is ImmichException)
            assertFalse(failure.message.orEmpty().contains("test-only-key"))
            assertFalse(failure.message.orEmpty().contains("private-server-diagnostic"))
        }
    }

    @Test
    fun oversizedMetadataIsRejectedBeforeJsonParsing() = runBlocking {
        withServer { server ->
            server.route("/api/albums") { Response(declaredLength = 3L * 1024 * 1024) }
            assertTrue(failure { ImmichClient("${server.origin}/api", "test-only-key").albums() } is ImmichResponseTooLarge)
        }
    }

    @Test
    fun findsAnImageThroughBoundedTimelineWithoutFetchingFullAlbum() = runBlocking {
        withServer { server ->
            val albumDetailRequests = AtomicInteger()
            server.route("/api/albums/$albumId") { albumDetailRequests.incrementAndGet(); Response(500) }
            server.route("/api/timeline/buckets") { Response(body = """[{"timeBucket":"2026-11-01T00:00:00.000Z"}]""") }
            server.route("/api/timeline/bucket") { request ->
                if (request.target.contains("albumId=$albumId") && request.target.contains("isTrashed=false"))
                    Response(body = """{"id":["$imageId"],"isImage":[true],"isTrashed":[false]}""")
                else Response(400)
            }
            assertEquals(imageId, ImmichClient("${server.origin}/api", "test-only-key").firstImage(albumId))
            assertEquals(0, albumDetailRequests.get())
        }
    }

    @Test
    fun oversizedTimelineBucketFallsBackToOneImageSearch() = runBlocking {
        withServer { server ->
            server.route("/api/timeline/buckets") { Response(body = """[{"timeBucket":"2026-11-01T00:00:00.000Z"}]""") }
            server.route("/api/timeline/bucket") { Response(declaredLength = 3L * 1024 * 1024) }
            server.route("/api/search/metadata") { request ->
                val body = JSONObject(request.body)
                if (request.method == "POST" && body.getJSONArray("albumIds").getString(0) == albumId &&
                    body.getString("type") == "IMAGE" && body.getInt("size") == 1 && !body.getBoolean("isTrashed"))
                    Response(body = """{"assets":{"items":[{"id":"$imageId","type":"IMAGE","isTrashed":false}]}}""")
                else Response(400)
            }
            assertEquals(imageId, ImmichClient("${server.origin}/api", "test-only-key").firstImage(albumId))
        }
    }

    private suspend fun failure(block: suspend () -> Unit): Exception {
        try { block() } catch (error: Exception) { return error }
        throw AssertionError("Expected the request to fail")
    }

    private suspend fun withServer(block: suspend (TestServer) -> Unit) {
        val server = TestServer()
        try { block(server) } finally { server.close() }
    }

    private data class Request(val method: String, val target: String, val headers: Map<String, String>, val body: String)
    private data class Response(val status: Int = 200, val body: String = "", val headers: Map<String, String> = emptyMap(), val declaredLength: Long? = null)

    /** Local HTTP fixture uses java.net only, supported by Android's compilation bootclasspath. */
    private class TestServer {
        private val handlers = ConcurrentHashMap<String, (Request) -> Response>()
        private val socket = ServerSocket().apply { bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0)) }
        val origin = "http://127.0.0.1:${socket.localPort}"
        private val worker = Thread {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { client ->
                        client.soTimeout = 5_000
                        val input = client.getInputStream()
                        val requestLine = input.line().split(' ')
                        val headers = mutableMapOf<String, String>()
                        while (true) {
                            val line = input.line()
                            if (line.isEmpty()) break
                            headers[line.substringBefore(':').lowercase(Locale.ROOT)] = line.substringAfter(':').trim()
                        }
                        val body = ByteArray(headers["content-length"]?.toInt() ?: 0)
                        var offset = 0
                        while (offset < body.size) {
                            val count = input.read(body, offset, body.size - offset)
                            if (count < 0) break
                            offset += count
                        }
                        val request = Request(requestLine[0], requestLine[1], headers, body.toString(Charsets.UTF_8))
                        val response = handlers[request.target.substringBefore('?')]?.invoke(request) ?: Response(404)
                        val bytes = response.body.toByteArray(Charsets.UTF_8)
                        val header = buildString {
                            append("HTTP/1.1 ${response.status} Test\r\nContent-Type: application/json\r\nConnection: close\r\n")
                            append("Content-Length: ${response.declaredLength ?: bytes.size}\r\n")
                            response.headers.forEach { (name, value) -> append("$name: $value\r\n") }
                            append("\r\n")
                        }
                        client.getOutputStream().apply { write(header.toByteArray(Charsets.ISO_8859_1)); write(bytes); flush() }
                    }
                } catch (error: SocketException) {
                    if (!socket.isClosed) throw error
                }
            }
        }.apply { isDaemon = true; start() }

        fun route(path: String, handler: (Request) -> Response) { handlers[path] = handler }
        fun close() { socket.close(); worker.join(1_000) }

        private fun InputStream.line(): String = buildString {
            while (true) {
                val character = read()
                if (character == -1 || character == 10) break
                if (character != 13) append(character.toChar())
                require(length <= 16_384) { "Fixture request line too long" }
            }
        }
    }
}
