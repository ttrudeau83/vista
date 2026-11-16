package dev.drewett.vista.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import dev.drewett.vista.ui.theme.LocalVistaAppearance
import dev.drewett.vista.ui.theme.appearanceColor

@Composable
fun Modifier.focusOutline(shape:Shape=RoundedCornerShape(16.dp)):Modifier {
    var focused by remember { mutableStateOf(false) }
    val color=appearanceColor(LocalVistaAppearance.current.focus)
    return onFocusChanged { focused=it.hasFocus }.then(if(focused) Modifier.border(3.dp,color,shape) else Modifier)
}

/** A single, small focus animation that never changes layout or D-pad targets. */
@Composable
fun Modifier.focusPop(enabled: Boolean = true, focusedScale: Float = 1.045f,shape:Shape=RoundedCornerShape(16.dp)): Modifier {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (enabled && focused) focusedScale else 1f,
        spring(dampingRatio = 0.78f, stiffness = 550f), label = "focus pop",
    )
    return onFocusChanged { focused = it.hasFocus }.graphicsLayer { scaleX = scale; scaleY = scale }.focusOutline(shape)
}
