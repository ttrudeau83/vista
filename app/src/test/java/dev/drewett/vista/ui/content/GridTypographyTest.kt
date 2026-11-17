package dev.drewett.vista.ui.content

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.drewett.vista.ui.theme.VistaTypography
import org.junit.Assert.*
import org.junit.Test

class GridTypographyTest {
    @Test fun actualVistaCardTypographyIsSafeAtTvFontScales() {
        for (scale in listOf(1f, 1.3f, 2f)) {
            for (style in listOf(VistaTypography.titleMedium, VistaTypography.labelLarge)) {
                val height = with(Density(2f, scale)) { gridLineHeight(style.lineHeight, style.fontSize).toDp().value }
                assertTrue(height.isFinite() && height > 0)
            }
        }
    }

    @Test fun unspecifiedThemeLineHeightCanBeConvertedWithoutCrashing() {
        val density = Density(2f, 1.5f)
        val resolved = gridLineHeight(TextUnit.Unspecified, 18.sp)
        val height = with(density) { resolved.toDp().value }
        assertTrue(height.isFinite())
        assertTrue(height >= 18f * 1.5f)
    }

    @Test fun relativeLineHeightIsResolvedBeforeDensityConversion() {
        val density = Density(2f, 1.25f)
        val resolved = gridLineHeight(1.5.em, 16.sp)
        assertEquals(24.sp, resolved)
        // Android may apply nonlinear font scaling; compare to the equivalent Sp style.
        assertEquals(with(density) { 24.sp.toDp().value }, with(density) { resolved.toDp().value }, 0.01f)
    }

    @Test fun explicitLineHeightIsPreservedAndMissingFontSizeHasAFiniteFallback() {
        assertEquals(24.sp, gridLineHeight(24.sp, 18.sp))
        val height = with(Density(1f)) { gridLineHeight(TextUnit.Unspecified, TextUnit.Unspecified).toDp().value }
        assertTrue(height.isFinite() && height > 0)
    }
}
