package dev.drewett.vista.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.tv.material3.MaterialTheme

/** Quiet navy/violet/teal depth, drawn once per size without a bitmap or animated backdrop. */
@Composable
fun Modifier.vistaBackdrop(): Modifier {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return drawWithCache {
        val base = Brush.linearGradient(
            if (dark) listOf(Color(0xFF14232F), Color(0xFF131321), Color(0xFF092329))
            else listOf(Color(0xFFE4EEF5), Color(0xFFECE8F5), Color(0xFFDDEFEF)),
            start = Offset.Zero, end = Offset(size.width, size.height),
        )
        val shade = Brush.radialGradient(
            listOf(Color.Transparent, if (dark) Color(0x6605070E) else Color(0x15FFFFFF)),
            center = Offset(size.width * 0.22f, size.height * 0.12f),
            radius = size.maxDimension.coerceAtLeast(1f),
        )
        onDrawBehind { drawRect(base); drawRect(shade) }
    }
}
