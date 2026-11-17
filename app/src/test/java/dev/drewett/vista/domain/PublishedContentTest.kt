package dev.drewett.vista.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class PublishedContentTest {
    @Test fun githubNuvioTakesPrecedenceWhenBothOfficialAppsAreInstalled() {
        assertEquals("com.nuvio.tv", PublishedContent.preferredNuvioPackage(listOf("com.nuvio.app", "com.nuvio.tv")))
        assertEquals("com.nuvio.tv", PublishedContent.preferredNuvioPackage(listOf("com.nuvio.tv", "com.nuvio.app")))
    }

    @Test fun vistaShellForkTakesPrecedenceOverOfficialNuvio() {
        assertEquals(PublishedContent.NUVIO_SHELL_PACKAGE,
            PublishedContent.preferredNuvioPackage(listOf("com.nuvio.tv", PublishedContent.NUVIO_SHELL_PACKAGE, "com.nuvio.app")))
    }

    @Test fun nuvioShortcutFallsBackToRemainingInstallation() {
        assertEquals("com.nuvio.app", PublishedContent.preferredNuvioPackage(listOf("com.nuvio.app")))
        assertEquals("com.nuviodebug.com", PublishedContent.preferredNuvioPackage(listOf("com.nuviodebug.com")))
        assertNull(PublishedContent.preferredNuvioPackage(listOf("other.app")))
    }

    @Test fun uninstallOrDisableRemovesAllPublisherRows() {
        val cards = listOf(card("removed.app"), card("enabled.app"), card(null))
        assertEquals(listOf("enabled.app"), PublishedContent.available(cards, setOf("enabled.app")).map { it.packageName })
    }

    @Test fun identicalTitlesFromDifferentPublishersAreNotDropped() {
        assertEquals(2, PublishedContent.distinct(listOf(card("one.app"), card("two.app"))).size)
    }

    @Test fun samePublishedIdentityAcrossTablesPrefersTheFirstWatchNextEntry() {
        val watch = card("one.app").copy(source = "watchnext", internalProviderId = "published-item")
        val preview = watch.copy(id = 22, source = "preview")
        assertEquals(listOf(watch), PublishedContent.distinct(listOf(watch, preview)))
        assertFalse(watch.stableKey == preview.stableKey)
    }

    @Test fun continuationClassificationUsesExplicitChannelMetadata() {
        assertTrue(PublishedContent.isContinueWatchingChannel("Continue watching"))
        assertTrue(PublishedContent.isContinueWatchingChannel("Watch next"))
        assertFalse(PublishedContent.isContinueWatchingChannel("Trending"))
        assertFalse(PublishedContent.isContinueWatchingChannel(null))
        assertTrue(PublishedContent.isSmartTube("org.smarttube.stable"))
        assertFalse(PublishedContent.isSmartTube("some.other.app"))
    }

    private fun card(packageName: String?) = ContentCard(
        id = 1, title = "Same title", description = null, posterArtUri = null,
        intentUri = "intent://published-item", packageName = packageName, providerIcon = null,
        seasonNumber = null, episodeNumber = null, episodeTitle = null, reviewRating = null,
        reviewRatingStyle = -1, releaseDate = null, type = 0, aspectRatioCode = 0,
        durationMillis = 100, positionMillis = 10, lastEngagement = 0,
    )
}
