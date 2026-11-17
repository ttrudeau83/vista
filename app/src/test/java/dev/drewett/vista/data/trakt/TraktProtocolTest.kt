package dev.drewett.vista.data.trakt

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TraktProtocolTest {
    @Test fun removalTargetsOnlyOneMediaTypeAndTheSelectedList() {
        val movie = TraktProtocol.removalBody("movie", 12)
        assertEquals(listOf("movies"), movie.keys().asSequence().toList())
        assertEquals(1, movie.getJSONArray("movies").length())
        assertEquals(12L, movie.getJSONArray("movies").getJSONObject(0).getJSONObject("ids").getLong("trakt"))
        assertTrue(TraktProtocol.removalBody("show",12).has("shows"))
        assertEquals("/sync/watchlist/remove", TraktProtocol.removalPath(null))
        assertEquals("/users/me/lists/42/items/remove", TraktProtocol.removalPath(42))
    }
    @Test fun removalNeedsAnExplicitSingleDeletionWithoutNotFoundItems() {
        assertTrue(TraktProtocol.removalConfirmed("movie", JSONObject("""{"deleted":{"movies":1,"shows":0},"not_found":{"movies":[],"shows":[]}}""")))
        assertTrue(TraktProtocol.removalConfirmed("show", JSONObject("""{"deleted":{"shows":1},"not_found":{}}""")))
        listOf(
            """{}""",
            """{"deleted":{"movies":0},"not_found":{}}""",
            """{"deleted":{"movies":1}}""",
            """{"deleted":{"movies":1,"shows":1},"not_found":{}}""",
            """{"deleted":{"movies":1},"not_found":{"movies":[{"ids":{"trakt":12}}]}}""",
            """{"deleted":{"movies":1},"not_found":{"movies":null}}"""
        ).forEach { assertFalse(TraktProtocol.removalConfirmed("movie",JSONObject(it))) }
        assertFalse(TraktProtocol.removalConfirmed("show",JSONObject("""{"deleted":{"movies":1},"not_found":{}}""")))
    }
    @Test fun invalidRemovalIdentityCannotProduceAMutationRequest() {
        listOf("episode", "", "movies").forEach { type ->
            assertTrue(runCatching { TraktProtocol.removalBody(type, 12) }.isFailure)
        }
        assertTrue(runCatching { TraktProtocol.removalBody("movie",0) }.isFailure)
        assertTrue(runCatching { TraktProtocol.removalPath(-1) }.isFailure)
    }
    @Test fun mixedListsKeepMovieAndShowIdentityWithoutEpisodes() {
        val items=TraktProtocol.items(JSONArray("""[
            {"type":"movie","movie":{"title":"One","year":2026,"overview":"Story","ids":{"trakt":12,"imdb":"tt123","tmdb":8},"rating":8.2,"images":{"poster":["walter-r2.trakt.tv/poster.webp"]}}},
            {"type":"show","show":{"title":"One","ids":{"trakt":12}}},
            {"type":"episode","episode":{"title":"Episode","ids":{"trakt":44}}},
            {"type":"movie","movie":{"title":"One duplicate","ids":{"trakt":12}}},
            {"type":"movie","movie":{"title":"Missing identity","ids":{}}}
        ]"""))
        assertEquals(2,items.size)
        assertEquals("https://walter-r2.trakt.tv/poster.webp",items[0].posterUrl)
        assertEquals("tt123",items[0].imdbId)
        assertEquals(2026,items[0].year)
        assertEquals("show",items[1].type)
        assertNull(items[1].year)
        assertNull(items[1].rating)
    }
    @Test fun malformedOrExternalImageUrlsAreNeverDownloaded() {
        listOf("http://trakt.tv/a","https://trakt.tv.attacker.example/a","https://localhost/a","https://user@trakt.tv/a","https://trakt.tv:443/a").forEach { assertNull(TraktProtocol.imageUrl(it)) }
        assertNotNull(TraktProtocol.imageUrl("walter-r2.trakt.tv/images/poster.webp"))
    }
    @Test fun deviceResponsesDistinguishPendingThrottleAndTerminalFailures() {
        assertEquals(TraktProtocol.Poll.SUCCESS,TraktProtocol.poll(200))
        assertEquals(TraktProtocol.Poll.PENDING,TraktProtocol.poll(400))
        assertEquals(TraktProtocol.Poll.SLOW_DOWN,TraktProtocol.poll(429))
        listOf(404,409,410,418,401,403).forEach { assertEquals(TraktProtocol.Poll.STOP,TraktProtocol.poll(it)) }
        assertEquals(TraktProtocol.Poll.RETRY,TraktProtocol.poll(503))
    }
    @Test fun ownedListsNeedStableIdAndNameAndAreDeduplicated() {
        assertEquals(listOf(TraktList(123,"Family")),TraktProtocol.lists(JSONArray("""[
            {"name":"Family","ids":{"trakt":123}},{"name":"Duplicate","ids":{"trakt":123}},
            {"name":"No id"},{"ids":{"trakt":4}}
        ]""")))
    }
}
