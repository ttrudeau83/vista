package dev.drewett.vista.data.immich

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AmbientRotationPolicyTest {
    @Test fun readyClipsGetAnOccasionalTurnWithoutRepeatingShownVideos() {
        val photo=PhotoAsset("photo",cachedFile=temp.newFile("photo"))
        val video=PhotoAsset("video",cachedFile=temp.newFile("video"),kind=MediaKind.VIDEO)
        assertEquals(video,AmbientRotationPolicy.ready(listOf(photo,video),emptySet(),null,0,preferVideo=true))
        assertEquals(photo,AmbientRotationPolicy.ready(listOf(photo,video),setOf(video.id),null,0,preferVideo=true))
    }
    @Test fun largeVideosCannotCauseEndlessOriginalEvictionAndRedownload() {
        assertFalse(PhotoCachePolicy.canWarm(512L*1024*1024,true))
        assertTrue(PhotoCachePolicy.canWarm(512L*1024*1024,false))
        assertTrue(PhotoCachePolicy.canWarm(100L*1024*1024,true))
    }
    @get:Rule val temp=TemporaryFolder()
    @Test fun displayedAssetsNeverRepeatWhileAnUnseenCachedAssetExists() {
        val assets=(0..9).map { PhotoAsset("id-$it",cachedFile=temp.newFile("$it")) }
        val shown=mutableSetOf<String>();var previous:String?=null
        repeat(10) { index ->
            val next=AmbientRotationPolicy.ready(assets,shown,previous,index-5)!!
            assertTrue(shown.add(next.id));assertNotEquals(previous,next.id);previous=next.id
        }
        assertNull(AmbientRotationPolicy.ready(assets,shown,previous,0))
        assertFalse(AmbientRotationPolicy.cycleComplete(assets,shown,false))
        assertTrue(AmbientRotationPolicy.cycleComplete(assets,shown,true))
    }
    @Test fun prefetchMovesForwardAfterViewingAndKeepsOnlyTwoVideoCandidates() {
        val assets=(0..99).map { PhotoAsset("photo-$it") }+(0..9).map { PhotoAsset("video-$it",kind=MediaKind.VIDEO) }
        val first=AmbientRotationPolicy.window(assets,emptySet())
        assertEquals(60,first.count { !it.isVideo });assertEquals(2,first.count { it.isVideo })
        val viewed=setOf("photo-0","video-0")
        val next=AmbientRotationPolicy.window(assets,viewed)
        assertTrue(next.none { it.id in viewed });assertTrue(next.any { it.id=="photo-60" });assertTrue(next.any { it.id=="video-2" })
    }
    @Test fun uncachedAndHiddenAssetsCannotBecomeReadyMedia() {
        val hidden=PhotoAsset("hidden",cachedFile=temp.newFile("hidden"),hidden=true)
        assertNull(AmbientRotationPolicy.ready(listOf(hidden,PhotoAsset("not-downloaded")),emptySet(),null,0))
    }
}
