package dev.drewett.vista.data.music

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MusicProtocolTest {
    @Test fun validatesOnlySafeYouTubeVideoIdentifiers() {
        assertTrue(MusicProtocol.videoId("abcDEF_-123"))
        assertFalse(MusicProtocol.videoId("../../token"))
        assertFalse(MusicProtocol.videoId("abc'<script"))
        assertFalse(MusicProtocol.videoId(""))
    }
    @Test fun identityComesFromAuthorizedChannelResponse() {
        val channels=MusicProtocol.channels(JSONObject("""{"items":[{"id":"UC_actual_123","snippet":{"title":"Blanktarget Records"}}]}"""))
        assertEquals("UC_actual_123",channels.single().id)
        assertEquals("Blanktarget Records",channels.single().title)
    }
    @Test fun playlistResponsePreservesNamesCountsAndSecureArtwork() {
        val playlists=MusicProtocol.playlists(JSONObject("""{"items":[{"id":"PL_123","snippet":{"title":"My music","thumbnails":{"medium":{"url":"https://i.ytimg.com/example.jpg"}}},"contentDetails":{"itemCount":4}}]}"""))
        assertEquals("My music",playlists.single().title)
        assertEquals(4,playlists.single().count)
        assertTrue(playlists.single().thumbnail!!.startsWith("https://"))
    }
    @Test fun missingPrivateAndInvalidPlaylistVideosAreSkipped() {
        val tracks=MusicProtocol.tracks(JSONObject("""{"items":[{"snippet":{"title":"Private video","resourceId":{"videoId":"abcDEF_-123"}}},{"snippet":{"title":"Unsafe","resourceId":{"videoId":"../invalid"}}},{"snippet":{"title":"Real title","resourceId":{"videoId":"xyzDEF_-123"},"thumbnails":{"default":{"url":"http://example.com/plain.jpg"}}}}]}"""))
        assertEquals(1,tracks.size)
        assertEquals("Real title",tracks.single().title)
        assertNull(tracks.single().thumbnail)
    }
    @Test fun emptyLibraryDoesNotInventPlaylistsOrTracks() {
        assertTrue(MusicProtocol.playlists(JSONObject("{}")).isEmpty())
        assertTrue(MusicProtocol.tracks(JSONObject("{\"items\":[]}")).isEmpty())
    }
    @Test fun pendingDeviceAuthorizationSurvivesCredentialSerializationWithoutPublicDeviceCode() {
        val credentials=MusicCredentials("client","secret",pendingDeviceCode="private-device-grant",
            pendingUserCode="VISIBLE-CODE",pendingVerificationUrl="https://www.google.com/device",pendingExpiresAt=20_000,pendingInterval=10)
        val restored=MusicCredentials.fromJson(JSONObject(credentials.toJson().toString()))
        assertEquals("private-device-grant",restored.pendingDeviceCode)
        assertEquals(10L,restored.pendingInterval)
        val pairing=restored.pendingPairing(10_000)!!
        assertEquals("VISIBLE-CODE",pairing.userCode)
        assertFalse(pairing.toString().contains("private-device-grant"))
        assertEquals("MusicCredentials(redacted)",restored.toString())
        assertNull(restored.pendingPairing(30_000))
    }
    @Test fun clearingPendingAuthorizationRemovesEveryPersistedGrantField() {
        val credentials=MusicCredentials("client","secret",pendingDeviceCode="private-device-grant",
            pendingUserCode="VISIBLE-CODE",pendingVerificationUrl="https://www.google.com/device",pendingExpiresAt=20_000)
        credentials.clearPending()
        assertNull(credentials.pendingPairing(0))
        assertFalse(credentials.toJson().toString().contains("private-device-grant"))
        assertFalse(credentials.toJson().toString().contains("VISIBLE-CODE"))
    }
}
