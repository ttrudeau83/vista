package dev.drewett.vista.data.immich

import java.io.File
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhotoCatalogTest {
    @Test fun dailyPoolIsStableForTheDayAndChangesAcrossDays() {
        val assets = (0 until 100).map { PhotoAsset("photo-$it") }
        val day = LocalDate.of(2026, 11, 16)
        val today = PhotoPolicy.dailyPool(assets,day)
        assertEquals(12,today.size)
        assertEquals(today,PhotoPolicy.dailyPool(assets.reversed(),day))
        assertNotEquals(today,PhotoPolicy.dailyPool(assets,day.plusDays(1)))
    }

    @Test fun dailyPoolRemainsUsableWithSmallAlbums() {
        val asset = PhotoAsset("one")
        assertEquals(listOf(asset),PhotoPolicy.dailyPool(listOf(asset,asset)))
        assertTrue(PhotoPolicy.dailyPool(emptyList()).isEmpty())
    }
    @get:Rule val temporary = TemporaryFolder()
    private val first = "11111111-1111-4111-8111-111111111111"
    private val second = "22222222-2222-4222-8222-222222222222"

    @Test fun catalogUsesPhotographerLocalDateAndSkipsVideosAndTrash() {
        val page = ImmichProtocol.photoPage("""{"assets":{"total":140,"items":[
            {"id":"$first","type":"IMAGE","localDateTime":"2020-11-15T23:30:00Z","fileCreatedAt":"2020-11-16T06:30:00Z"},
            {"id":"$second","type":"VIDEO"}, {"id":"invalid","type":"IMAGE"},
            {"id":"$second","type":"IMAGE","isTrashed":true}]}}""")
        assertEquals(140, page.total)
        assertEquals(listOf(PhotoAsset(first, "2020-11-15")), page.assets)
    }

    @Test fun continuationIsAuthoritativeWhenTotalIsOnlyTheCurrentPageCount() {
        val page = ImmichProtocol.photoPage("""{"assets":{"total":100,"count":100,"nextPage":"2","items":[
            {"id":"$first","type":"IMAGE"}]}}""")
        assertEquals(100, page.total)
        assertEquals(2, page.nextPage)
        val finished = ImmichProtocol.photoPage("""{"assets":{"total":100,"nextPage":null,"items":[]}}""")
        assertNull(finished.nextPage)
    }

    @Test fun onThisDayUsesMonthAndDayAcrossYearsAndNeverIncludesHiddenPhotos() {
        val assets = listOf(PhotoAsset(first, "2018-11-15"), PhotoAsset(second, "2024-11-15"),
            PhotoAsset("third", "2024-11-14"), PhotoAsset("current-year", "2026-11-15"), PhotoAsset("future", "2027-11-15"))
        val selected = PhotoPolicy.eligible(assets, setOf(first), SlideshowMode.ON_THIS_DAY, LocalDate.of(2026, 11, 15))
        assertEquals(listOf(second), selected.map { it.id })
    }

    @Test fun onThisDayWithoutAnniversariesFallsBackToVisibleAlbumPhotos() {
        val assets = listOf(PhotoAsset(first, "2018-02-29"), PhotoAsset(second, null))
        assertEquals(listOf(second), PhotoPolicy.eligible(assets, setOf(first), SlideshowMode.ON_THIS_DAY,
            LocalDate.of(2026, 11, 15)).map { it.id })
    }

    @Test fun twelveCachedPhotosNavigateWithoutSelectingAnUncachedAssetOrRepeatingImmediately() {
        val cached = (0 until 12).map { index ->
            PhotoAsset("cached-$index", cachedFile = temporary.newFile("$index.image").apply { writeText("cached") })
        }
        val uncached = PhotoAsset("uncached", cachedFile = File(temporary.root, "not-downloaded"))
        var previous: String? = null
        repeat(50) { index ->
            val next = PhotoPolicy.cachedNext(cached + uncached, previous, index - 25)
            assertNotNull(next)
            assertNotEquals("uncached", next!!.id)
            assertNotEquals(previous, next.id)
            previous = next.id
        }
    }

    @Test fun cachedNavigationReturnsNullWhenNoFilesExist() {
        assertNull(PhotoPolicy.cachedNext(listOf(PhotoAsset(first, cachedFile = File(temporary.root, "absent"))), null, 0))
    }

    @Test fun bootstrapPreviewIsImmediatelyUsableWithoutRequiringTheOriginalToFinish() {
        val preview = PhotoAsset(first, cachedFile = temporary.newFile("preview.image"), quality = PhotoQuality.PREVIEW)
        val pendingOriginal = PhotoAsset(second)
        assertEquals(preview, PhotoPolicy.cachedNext(listOf(pendingOriginal, preview), null, 0))
        assertEquals(PhotoQuality.PREVIEW, preview.quality)
    }

    @Test fun lruKeepsAtLeastTwelveNormalPhotosInsideTheDiskBudget() {
        val twelve = (0 until 12).map { CachedPhotoEntry("photo-$it", 12L * 1024 * 1024, it.toLong()) }
        assertTrue(PhotoCachePolicy.evictionKeys(twelve, emptySet()).isEmpty())
    }

    @Test fun lruEvictsOldestFilesUntilBudgetIsMetButProtectsDisplayedPhoto() {
        val files = (0 until 8).map { CachedPhotoEntry("photo-$it", 40L * 1024 * 1024, it.toLong()) }
        val evicted = PhotoCachePolicy.evictionKeys(files, setOf("photo-0", "photo-7"))
        assertEquals(setOf("photo-1", "photo-2", "photo-3"), evicted)
        assertEquals(200L * 1024 * 1024, files.filter { it.key !in evicted }.sumOf { it.bytes })
    }

    @Test fun hiddenIdsFilterCachedCandidatesAsWellAsUncachedMetadata() {
        val file = temporary.newFile("hidden.image")
        val assets = listOf(PhotoAsset(first, cachedFile = file), PhotoAsset(second))
        assertNull(PhotoPolicy.cachedNext(PhotoPolicy.eligible(assets, setOf(first), SlideshowMode.RANDOM), null, 0))
    }
}
