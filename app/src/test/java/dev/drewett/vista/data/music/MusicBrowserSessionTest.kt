package dev.drewett.vista.data.music

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MusicBrowserSessionTest {
    @Test fun acceptsRawAndAlternatingHeadersButReplacesCapturedAuthorization() {
        val raw="""Cookie: SID=other; SAPISID=test-session
            |Authorization: SAPISIDHASH expired-do-not-reuse
            |X-Goog-AuthUser: 2
            |X-Goog-Visitor-Id: visitor_token
            |Origin: https://music.youtube.com
            |X-Unrelated: ignore-this
        """.trimMargin().trim()
        val session=MusicBrowserSession.parse(raw)
        val headers=session.headers(1700000000)
        assertEquals("2",headers["X-Goog-AuthUser"])
        assertEquals("visitor_token",headers["X-Goog-Visitor-Id"])
        assertFalse(headers.values.any { "expired" in it || "ignore-this" in it })
        val alternate=MusicBrowserSession.parse("cookie\nSAPISID=test-session\nx-goog-authuser\n2\norigin\nhttps://music.youtube.com")
        assertEquals(headers["Authorization"],alternate.headers(1700000000)["Authorization"])
        val wrapped=MusicBrowserSession.parse("cookie:\nSAPISID=test-session;\nx-goog-authuser:\n2\norigin:\nhttps://music.youtube.com")
        assertEquals(headers["Authorization"],wrapped.headers(1700000000)["Authorization"])
    }

    @Test fun authorizationHashUsesFreshTimestampAndThirdPartySessionWhenAvailable() {
        val session=MusicBrowserSession.parse("cookie: SAPISID=wrong; __Secure-3PAPISID=test-session")
        assertEquals("SAPISIDHASH 1700000000_5a58774df9cadc3b0395d1d4aa6601e6252290b3",session.headers(1700000000)["Authorization"])
        assertNotEquals(session.headers(1700000000)["Authorization"],session.headers(1700000001)["Authorization"])
    }

    @Test fun rejectsWrongOriginMissingSessionInjectionDuplicatesAndOversize() {
        val invalid=listOf(
            "cookie: SAPISID=test\norigin: https://evil.invalid",
            "cookie: SID=not-a-signed-in-session",
            "cookie: SAPISID=test\ncookie: SAPISID=different",
            "cookie: SAPISID=test\nx-goog-authuser: 12",
            JSONObject().put("cookie","SAPISID=test\r\nInjected: bad").toString(),
            "cookie: SAPISID="+"x".repeat(25*1024),
        )
        invalid.forEach { raw -> assertThrows(IllegalArgumentException::class.java) { MusicBrowserSession.parse(raw) } }
    }

    @Test fun sessionRoundTripsEncryptedVaultPayloadAndNeverPrintsCredentials() {
        val session=MusicBrowserSession.parse(JSONObject().put("cookie","SAPISID=test-session")
            .put("x-goog-authuser","1").put("x-goog-pageid","123456789").put("x-origin",MusicBrowserSession.ORIGIN).toString())
        assertFalse(session.toString().contains("test-session"))
        val credentials=MusicCredentials("client","secret",browserSession=session,browserProfile="Actual profile")
        val restored=MusicCredentials.fromJson(credentials.toJson())
        assertEquals(session.headers(10),restored.browserSession!!.headers(10))
        assertEquals("Actual profile",restored.browserProfile)
        assertFalse(restored.toString().contains("secret"))
        assertNull(MusicCredentials.fromJson(JSONObject().put("clientId","old-client").put("clientSecret","old-secret")).browserSession)
    }

    @Test fun profileComesOnlyFromTheActiveAccountHeader() {
        val response=JSONObject("""{"actions":[{"openPopupAction":{"popup":{"multiPageMenuRenderer":{"header":{
          "activeAccountHeaderRenderer":{"accountName":{"runs":[{"text":"Actual profile"}]},"channelHandle":{"simpleText":"@actual"}}
        }}}}}]}""")
        assertEquals("Actual profile · @actual",MusicBrowserSession.profile(response))
        assertNull(MusicBrowserSession.profile(JSONObject("""{"accountName":{"simpleText":"Wrong outside active header"}}""")))
        assertTrue(MusicBrowserSession.matchesChannel("Blanktarget Records · @BlanktargetRecords","BlanktargetRecords"))
        assertTrue(MusicBrowserSession.matchesChannel("Blanktarget Records · @different-handle","BlanktargetRecords"))
        assertFalse(MusicBrowserSession.matchesChannel("Personal Account · @BlanktargetRecords","BlanktargetRecords"))
        assertFalse(MusicBrowserSession.matchesChannel("Personal Account · @personal","BlanktargetRecords"))
        assertFalse(MusicBrowserSession.matchesChannel("BlanktargetRecords",""))
    }

    @Test fun renewalUpdatesOnlyExistingCookiesAndIgnoresDeletions() {
        val session=MusicBrowserSession.parse(listOf("Cookie: SAPISID=test-session; SIDCC=old-cc; __Secure-3PSIDTS=old-ts",
            "X-Goog-AuthUser: 1","Origin: https://music.youtube.com").joinToString("\n"))
        val renewed=session.withRenewedCookies(listOf(
            "SIDCC=new-cc; expires=Wed, 17-Nov-2027 00:00:00 GMT; path=/; domain=.youtube.com; Secure",
            "__Secure-3PSIDTS=new-ts; Path=/; Secure; HttpOnly",
            "TRACKER=added; path=/",
            "SAPISID=EXPIRED; Max-Age=0"))
        val headers=requireNotNull(renewed).headers(1_700_000_000)
        val cookie=headers.getValue("Cookie")
        assertTrue(cookie.contains("SIDCC=new-cc"));assertTrue(cookie.contains("__Secure-3PSIDTS=new-ts"))
        assertTrue(cookie.contains("SAPISID=test-session"));assertFalse(cookie.contains("TRACKER"))
        assertEquals("1",headers["X-Goog-AuthUser"])
        assertNull(renewed.withRenewedCookies(listOf("SIDCC=new-cc; path=/")))
    }
}
