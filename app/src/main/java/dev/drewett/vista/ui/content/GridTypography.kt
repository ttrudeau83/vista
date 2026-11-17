package dev.drewett.vista.ui.content

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Compose only converts Sp to physical units; theme line heights may be Unspecified or Em. */
internal fun gridLineHeight(lineHeight: TextUnit, fontSize: TextUnit): TextUnit {
    val size = if (fontSize.isSp && fontSize.value.isFinite() && fontSize.value > 0) fontSize.value else 16f
    val height = when {
        lineHeight.isSp -> lineHeight.value
        lineHeight.isEm -> lineHeight.value * size
        else -> size * 1.3f
    }
    return (height.takeIf { it.isFinite() && it > 0 } ?: (size * 1.3f)).sp
}
