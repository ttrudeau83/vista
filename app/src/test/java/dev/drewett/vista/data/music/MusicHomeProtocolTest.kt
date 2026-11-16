package dev.drewett.vista.data.music

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MusicHomeProtocolTest {
    @Test fun homePreservesPersonalizedShelfTitlesAndMixEndpoints() {
        val json=JSONObject("""{"contents":{"sectionListRenderer":{"contents":[{"musicCarouselShelfRenderer":{
          "header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"runs":[{"text":"Mixed for you"}]}}},
          "contents":[{"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"My Supermix"}]},
          "navigationEndpoint":{"watchEndpoint":{"playlistId":"RDTMAK5_example"}},
          "thumbnailRenderer":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://example.org/art"}]}}}}},
          {"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"Unsafe"}]},"navigationEndpoint":{"urlEndpoint":{"url":"https://evil.invalid"}}}}]}}]}}}""")
        val shelf=MusicHomeProtocol.shelves(json).single()
        assertEquals("Mixed for you",shelf.title)
        assertEquals("My Supermix",shelf.cards.single().title)
        assertEquals("RDTMAK5_example",shelf.cards.single().playlistId)
        assertEquals("https://example.org/art",shelf.cards.single().thumbnail)
    }
    @Test fun queueSkipsUnavailableTracksAndPreservesPlaybackOrder() {
        val json=JSONObject("""{"contents":[
          {"playlistPanelVideoRenderer":{"videoId":"abcDEF_-123","title":{"runs":[{"text":"First song"}]}}},
          {"playlistPanelVideoRenderer":{"videoId":"invalid","title":{"simpleText":"Missing"}}},
          {"playlistPanelVideoRenderer":{"videoId":"xyzDEF_-123","title":{"simpleText":"Second song"}}},
          {"playlistPanelVideoRenderer":{"videoId":"abcDEF_-123","title":{"simpleText":"Duplicate"}}}] }""")
        assertEquals(listOf("First song","Second song"),MusicHomeProtocol.tracks(json).map {it.title})
    }
    @Test fun browseParsesAlbumRowsWithoutTreatingArtistLinksAsSongs() {
        val json=JSONObject("""{"musicShelfRenderer":{"contents":[{"musicResponsiveListItemRenderer":{
          "playlistItemData":{"videoId":"abcDEF_-123"},"flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{
          "text":{"runs":[{"text":"Album song"},{"text":" title"}]}}}]}}]}}""")
        assertEquals("Album song title",MusicHomeProtocol.tracks(json).single().title)
        assertTrue(MusicHomeProtocol.shelves(JSONObject("{}" )).isEmpty())
    }
    @Test fun continuationSupportsAdditionalHomeRows() {
        val json=JSONObject("""{"continuationContents":{"sectionListContinuation":{"continuations":[{"nextContinuationData":{"continuation":"next-page-token"}}]}}}""")
        assertEquals("next-page-token",MusicHomeProtocol.continuation(json))
    }

    @Test fun mixWatchPlaylistEndpointAndThumbnailOverlayRemainPlayable() {
        val json=JSONObject("""{"musicCarouselShelfRenderer":{
          "header":{"musicCarouselShelfBasicHeaderRenderer":{"strapline":{"simpleText":"Mixed for you"}}},
          "contents":[
            {"musicTwoRowItemRenderer":{"title":{"simpleText":"Supermix"},"navigationEndpoint":{"watchPlaylistEndpoint":{"playlistId":"RDTMAK5_mix"}}}},
            {"musicTwoRowItemRenderer":{"title":{"simpleText":"Album"},"navigationEndpoint":{"browseEndpoint":{"browseId":"MPREb_album"}},
              "thumbnailOverlay":{"musicItemThumbnailOverlayRenderer":{"content":{"musicPlayButtonRenderer":{"playNavigationEndpoint":{"watchPlaylistEndpoint":{"playlistId":"OLAK_album"}}}}}}}}
          ]}}""")
        val shelf=MusicHomeProtocol.shelves(json).single()
        assertEquals("Mixed for you",shelf.title)
        assertEquals(listOf("RDTMAK5_mix","OLAK_album"),shelf.cards.map { it.playlistId })
        val (endpoint,body)=MusicHomeProtocol.destination(shelf.cards.first())
        assertEquals("next",endpoint)
        assertEquals("RDTMAK5_mix",body.getString("playlistId"))
        assertTrue(body.getBoolean("enablePersistentPlaylistPanel"))
    }

    @Test fun vlMixUsesQueueWhileArtistRetainsBrowseDestination() {
        val mix=MusicHomeProtocol.destination(MusicHomeCard("Mix",null,browseId="VLRDTMAK5_mix"))
        assertEquals("next",mix.first)
        assertEquals("RDTMAK5_mix",mix.second.getString("playlistId"))
        val artist=MusicHomeProtocol.destination(MusicHomeCard("Artist",null,browseId="UC_artist"))
        assertEquals("browse",artist.first)
        assertEquals("UC_artist",artist.second.getString("browseId"))
        val seeded=MusicHomeProtocol.destination(MusicHomeCard("Seed",null,playlistId="RDAMVMabcDEF_-123",videoId="abcDEF_-123"))
        assertEquals("abcDEF_-123",seeded.second.getString("videoId"))
    }

    @Test fun queueUsesPrimaryTrackOnlyAndSkipsExplicitlyUnplayableRows() {
        val json=JSONObject("""{"contents":[
          {"playlistPanelVideoWrapperRenderer":{
            "primaryRenderer":{"playlistPanelVideoRenderer":{"videoId":"abcDEF_-123","title":{"simpleText":"Audio"}}},
            "counterpart":[{"counterpartRenderer":{"playlistPanelVideoRenderer":{"videoId":"xyzDEF_-123","title":{"simpleText":"Video counterpart"}}}}]}},
          {"playlistPanelVideoRenderer":{"videoId":"xyzDEF_-123","title":{"simpleText":"Unavailable"},"unplayableText":{"simpleText":"Not available"}}},
          {"musicResponsiveListItemRenderer":{"flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{
            "text":"Quick pick","navigationEndpoint":{"watchEndpoint":{"videoId":"aaaDEF_-123"}}}]}}}]}}
        ]}""")
        assertEquals(listOf("Audio","Quick pick"),MusicHomeProtocol.tracks(json).map { it.title })
    }
}
