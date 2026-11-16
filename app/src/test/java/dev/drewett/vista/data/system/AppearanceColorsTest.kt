package dev.drewett.vista.data.system

import org.junit.Assert.*
import org.junit.Test

class AppearanceColorsTest {
    @Test fun normalizesKeyboardInput() {
        assertEquals("#A1B2C3", AppearanceColors.normalize("  #a1b2c3  "))
        assertEquals("#00FF01", AppearanceColors.normalize("00ff01"))
    }
    @Test fun rejectsMalformedOrTransparentColors() {
        listOf("", "#FFF", "#11223344", "#12345G", "##112233", "12 3456").forEach {
            assertNull(AppearanceColors.normalize(it))
        }
    }
    @Test fun preservesOpaqueRgbIncludingLeadingZeros() {
        assertEquals(0xFF000000L, AppearanceColors.argb("#000000"))
        assertEquals(0xFFFFFFFFL, AppearanceColors.argb("#FFFFFF"))
        assertEquals(0xFF0012ABL, AppearanceColors.argb("#0012ab"))
    }
}
