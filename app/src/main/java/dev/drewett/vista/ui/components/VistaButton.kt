package dev.drewett.vista.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.ButtonColors
import androidx.tv.material3.ButtonDefaults

/** One focus treatment for remote buttons, including the configurable visible outline. */
@Composable
fun VistaButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    colors:ButtonColors=ButtonDefaults.colors(),contentPadding:PaddingValues=ButtonDefaults.ContentPadding,
    content:@Composable RowScope.()->Unit) {
    androidx.tv.material3.Button(onClick=onClick,modifier=modifier.focusPop(shape=RoundedCornerShape(50)),enabled=enabled,
        colors=colors,contentPadding=contentPadding,scale=ButtonDefaults.scale(focusedScale=1f),content=content)
}
