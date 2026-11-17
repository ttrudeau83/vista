package dev.drewett.vista.domain

import org.junit.Assert.*
import org.junit.Test

class NuvioTitleLinkTest {
    @Test fun movieAndSeriesUseTheSelectedIdentifier() {
        assertEquals("nuvio://meta?type=movie&id=tt123", NuvioTitleLink.create("movie", "tt123", 99))
        assertEquals("nuvio://meta?type=series&id=tt456", NuvioTitleLink.create("show", "tt456", null))
        assertEquals("nuvio://meta?type=series&tmdb=789", NuvioTitleLink.create("show", null, 789))
    }
    @Test fun invalidOrMissingIdentityDoesNotInventATitleLink() {
        assertNull(NuvioTitleLink.create("episode", "tt123", 3))
        assertNull(NuvioTitleLink.create("movie", "tt123&other=value", null))
        assertNull(NuvioTitleLink.create("show", null, -1))
    }
}
