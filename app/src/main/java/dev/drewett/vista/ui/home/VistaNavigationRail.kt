package dev.drewett.vista.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import dev.drewett.vista.R
import dev.drewett.vista.ui.components.focusPop
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val LocalNavigationRail=staticCompositionLocalOf<FocusRequester?> { null }
val LocalPanelReturnFocus=staticCompositionLocalOf<MutableState<FocusRequester?>?> { null }

@Composable
fun Modifier.panelFocus():Modifier {
    val remembered=LocalPanelReturnFocus.current ?: return this
    val self=remember { FocusRequester() }
    return focusRequester(self).onFocusChanged { if(it.isFocused) remembered.value=self }
}

/** Apply to the leading card/column so Left never jumps to an unrelated row. */
@Composable
fun Modifier.railBoundary():Modifier {
    val rail=LocalNavigationRail.current
    return if(rail==null) this else panelFocus().focusProperties { left=rail }
}

@Composable
fun VistaNavigationRail(selected:VistaTab,railFocus:FocusRequester,onEnter:(VistaTab)->Unit,
    onSearch:()->Unit,onSettings:()->Unit,onFocus:(Boolean)->Unit,use24Hour:Boolean,modifier:Modifier=Modifier,
    onNuvioLongPress:()->Unit={}) {
    var expanded by remember { mutableStateOf(false) }
    val context=androidx.compose.ui.platform.LocalContext.current
    // Movies/Shows only showed Trakt history, which Nuvio covers, so they're off the rail on both TVs.
    // Freeplay is a Google TV service; Fire OS has no equivalent, so it is left out there.
    val tabs=remember { VistaTab.entries.filter { it!=VistaTab.MOVIES && it!=VistaTab.SHOWS &&
        (it!=VistaTab.FREEPLAY || !dev.drewett.vista.data.system.Platform.isFireOs(context)) } }
    // Search leads the rail (index 0), then the tabs, then Settings — same order as the Nuvio shell.
    fun railIndex(tab:VistaTab)=tabs.indexOf(tab).coerceAtLeast(0)+1
    var lastFocus by remember { mutableStateOf(railIndex(selected)) }
    // Tabs can change from outside the rail (e.g. chosen in the Nuvio shell's copy of it).
    LaunchedEffect(selected) { lastFocus = railIndex(selected) }
    val itemFocus=remember { List(tabs.size+2) { FocusRequester() } }
    val width by animateDpAsState(if(expanded) 224.dp else 84.dp,tween(180),label="navigation width")
    val icons=listOf(R.drawable.ic_search)+tabs.map { it.icon }+listOf(R.drawable.ic_settings)
    val labels=listOf("Search")+tabs.map { it.label }+listOf("Settings")
    fun activate(index:Int) {
        when(index) {
            0 -> onSearch()
            tabs.size+1 -> onSettings()
            else -> onEnter(tabs[index-1])
        }
    }
    Column(modifier.width(width).fillMaxHeight().clipToBounds()
        .background(Brush.horizontalGradient(listOf(Color(0xFF0A1423),Color(0xF0141E30))))
        .focusRequester(railFocus).onFocusChanged {
            // However the rail gains focus (even before its items attach at startup), land on the current tab.
            if(it.hasFocus && !expanded) runCatching { itemFocus[lastFocus].requestFocus() }
            expanded=it.hasFocus;onFocus(it.hasFocus) }
        .focusProperties { enter={ itemFocus[lastFocus] } }.focusGroup().padding(vertical=12.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed { index,label ->
                val isSelected=index==railIndex(selected)
                Surface(onClick={ activate(index) },
                    onLongClick=if(index==railIndex(VistaTab.NUVIO)) onNuvioLongPress else null,modifier=Modifier.fillMaxWidth().height(42.dp).focusPop(focusedScale=1.025f)
                    .focusRequester(itemFocus[index]).onFocusChanged { if(it.isFocused) lastFocus=index }
                    .onPreviewKeyEvent { event ->
                        when(event.key) {
                            Key.DirectionUp,Key.DirectionDown -> {
                                if(event.type==KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount==0) {
                                    val next=(index+if(event.key==Key.DirectionDown) 1 else -1).coerceIn(itemFocus.indices)
                                    itemFocus[next].requestFocus()
                                }
                                true
                            }
                            Key.DirectionLeft -> true
                            Key.DirectionRight -> { if(event.type==KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount==0) activate(index);true }
                            else -> false
                        }
                    },shape=ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),scale=ClickableSurfaceDefaults.scale(focusedScale=1f),
                    colors=ClickableSurfaceDefaults.colors(containerColor=if(isSelected) Color(0xFF263D5F) else Color.Transparent,
                        contentColor=if(isSelected) Color.White else Color(0xFF9EADC4),focusedContainerColor=Color(0xFFD7E5FF),focusedContentColor=Color(0xFF0B1729))) {
                    Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically) {
                        Box(Modifier.width(64.dp).fillMaxHeight(),contentAlignment=Alignment.Center) {
                            Image(painterResource(icons[index]),label,Modifier.size(22.dp),colorFilter=ColorFilter.tint(LocalContentColor.current))
                        }
                        if(expanded) Text(label,maxLines=1,style=MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        var now by remember { mutableStateOf(Date()) }
        LaunchedEffect(Unit) { while(true) { now=Date();delay(20_000) } }
        Text(SimpleDateFormat(if(use24Hour) "HH:mm" else "h:mm a",Locale.getDefault()).format(now),
            Modifier.align(Alignment.CenterHorizontally).padding(top=10.dp),fontSize=12.sp,color=Color(0xFFB3C1D7))
    }
}
