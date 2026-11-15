package dev.drewett.vista.data.immich

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmichProtocolTest {
    private val imageId = "11111111-1111-4111-8111-111111111111"
    private val videoId = "22222222-2222-4222-8222-222222222222"
    private val trashedId = "33333333-3333-4333-8333-333333333333"

    @Test
    fun normalizesServerWithoutDuplicatingApiOrLosingReverseProxyPath() {
        assertEquals("https://photos.example/api", ImmichProtocol.normalizeServer("https://photos.example/"))
        assertEquals("https://photos.example/api", ImmichProtocol.normalizeServer("https://photos.example/api/"))
        assertEquals("https://photos.example/immich/api", ImmichProtocol.normalizeServer("https://photos.example/immich/"))
    }

    @Test
    fun rejectsCredentialsAndAmbiguousServerAddresses() {
        listOf(
            "https://user:password@photos.example",
            "https://photos.example?api_key=private",
            "https://photos.example/#fragment",
            "file:///tmp/photos",
            "ftp://photos.example",
            "photos.example",
            "https:///missing-host",
            "https://photos.example/../other",
            "https://photos.example/%2e%2e/other",
            "https://photos.example:0",
        ).forEach { server ->
            assertFails("Unexpectedly accepted $server") { ImmichProtocol.normalizeServer(server) }
        }
    }

    @Test
    fun requiresExplicitOptInForUnencryptedHttp() {
        assertFails("HTTP should require opt-in") { ImmichProtocol.normalizeServer("http://192.168.0.2:2283") }
        assertEquals(
            "http://192.168.0.2:2283/api",
            ImmichProtocol.normalizeServer("http://192.168.0.2:2283", allowHttp = true),
        )
    }

    @Test
    fun redirectOriginChecksRespectSchemeHostAndEffectivePort() {
        val origin = URI("https://photos.example/api/assets/one/thumbnail")
        assertTrue(ImmichProtocol.sameOrigin(origin, URI("https://PHOTOS.EXAMPLE:443/api/assets/one/original")))
        assertFalse(ImmichProtocol.sameOrigin(origin, URI("https://other.example/api/assets/one/original")))
        assertFalse(ImmichProtocol.sameOrigin(origin, URI("http://photos.example/api/assets/one/original")))
        assertFalse(ImmichProtocol.sameOrigin(origin, URI("https://photos.example:8443/api/assets/one/original")))
        assertFalse(ImmichProtocol.sameOrigin(origin, URI("https://user:pass@photos.example/api/assets/one/original")))
    }

    @Test
    fun recognizesAlbumsAndHandlesEmptyLibrary() {
        val albums = ImmichProtocol.albums("""[{"id":"$imageId","albumName":"Family"}]""")
        assertEquals(1, albums.size)
        assertEquals(ImmichAlbum(imageId, "Family"), albums.single())
        assertTrue(ImmichProtocol.albums("[]").isEmpty())
        assertFails("Malformed response must not become success") { ImmichProtocol.albums("not-json") }
    }

    @Test
    fun malformedAlbumEntriesCannotSupplyPathsAndDuplicateIdsAreCollapsed() {
        val albums = ImmichProtocol.albums("""[
            {"id":"../../private","albumName":"Invalid path"},
            {"id":"$videoId","albumName":" "},
            {"id":"$imageId","albumName":"Family"},
            {"id":"$imageId","albumName":"Duplicate"}
        ]""")
        assertEquals(listOf(ImmichAlbum(imageId, "Family")), albums)
    }

    @Test
    fun legacyAlbumSelectionSkipsVideoAndTrashedAssets() {
        val response = """{"assets":[
            {"id":"$videoId","type":"VIDEO","isTrashed":false},
            {"id":"$trashedId","type":"IMAGE","isTrashed":true},
            {"id":"$imageId","type":"IMAGE","isTrashed":false}
        ]}"""
        assertEquals(imageId, ImmichProtocol.firstAlbumImage(response))
        assertNull(ImmichProtocol.firstAlbumImage("""{"assets":[]}"""))
        assertNull(ImmichProtocol.firstAlbumImage("""{"albumName":"Modern metadata without assets"}"""))
    }

    @Test
    fun currentColumnarBucketSkipsVideoAndTrashedAssets() {
        val response = """{
            "id":["$videoId","$trashedId","$imageId"],
            "isImage":[false,true,true],
            "isTrashed":[false,true,false]
        }"""
        assertEquals(imageId, ImmichProtocol.firstBucketImage(response))
    }

    @Test
    fun boundedSearchSelectionIgnoresVideoTrashedAndInvalidAssets() {
        val response = """{"assets":{"items":[
            {"id":"$videoId","type":"VIDEO"},
            {"id":"$trashedId","type":"IMAGE","isTrashed":true},
            {"id":"../secret","type":"IMAGE"},
            {"id":"$imageId","type":"IMAGE","isTrashed":false}
        ]}}"""
        assertEquals(imageId, ImmichProtocol.firstSearchImage(response))
        assertNull(ImmichProtocol.firstSearchImage("""{"assets":{"items":[]}}"""))
    }

    @Test
    fun malformedBucketFlagsDoNotSelectAnUnknownAsset() {
        assertNull(ImmichProtocol.firstBucketImage("""{"id":["$imageId"],"isImage":[],"isTrashed":[false]}"""))
        assertNull(ImmichProtocol.firstBucketImage("""{"id":["$imageId"],"isImage":["maybe"],"isTrashed":[false]}"""))
        assertNull(ImmichProtocol.firstBucketImage("""{"id":["../secret"],"isImage":[true],"isTrashed":[false]}"""))
    }

    @Test
    fun bucketDiscoveryPreservesServerOrderAndSupportsEmptyAlbums() {
        assertEquals(
            listOf("2026-11-01T00:00:00.000Z", "2026-10-01T00:00:00.000Z"),
            ImmichProtocol.buckets("""[
                {"timeBucket":"2026-11-01T00:00:00.000Z","count":2},
                {"timeBucket":"2026-10-01T00:00:00.000Z","count":1}
            ]"""),
        )
        assertTrue(ImmichProtocol.buckets("[]").isEmpty())
    }

    private fun assertFails(message: String, block: () -> Unit) {
        var failed = false
        try {
            block()
        } catch (_: Exception) {
            failed = true
        }
        assertTrue(message, failed)
    }
}
