package dev.drewett.vista.ui.music

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import dev.drewett.vista.MusicSettingsActivity
import dev.drewett.vista.ui.home.railBoundary
import dev.drewett.vista.ui.home.panelFocus
import dev.drewett.vista.R
import dev.drewett.vista.data.music.*
import dev.drewett.vista.ui.components.focusPop
import kotlinx.coroutines.launch

internal enum class MusicPage { HOME, LIBRARY, DETAIL, QUEUE }
internal data class MusicLocation(val page:MusicPage=MusicPage.HOME,val title:String="",val image:String?=null,
    val shelves:List<MusicShelf> = emptyList(),val tracks:List<MusicTrack> = emptyList(),val returnSection:Int=0)
internal class MusicBrowserState {
    val location=mutableStateOf(MusicLocation())
    val history=mutableStateListOf<MusicLocation>()
    val rows=LazyListState()
    val focusSection=mutableStateOf(0)
}

/** Browsing has its own history; opening another playlist never replaces the playing queue. */
@Composable
internal fun MusicBrowser(model:MusicViewModel,contentFocus:FocusRequester,hasPlayer:Boolean,
    queue:List<MusicTrack>,queueIndex:Int,queueRequest:Int,onPlay:(List<MusicTrack>,Int)->Unit,onBackRoot:()->Unit,navigation:MusicBrowserState) {
    val state by model.state.collectAsState()
    val busy by model.busy.collectAsState()
    val message by model.message.collectAsState()
    val context=LocalContext.current
    var location by navigation.location
    val history=navigation.history
    val rows=navigation.rows
    val heroFocus=contentFocus
    val playlistFocus=remember { FocusRequester() }
    val channelFocus=remember { FocusRequester() }
    val scope=rememberCoroutineScope()
    var focusSection by navigation.focusSection
    var openQueueObserved by remember { mutableStateOf(queueRequest) }
    fun navigate(next:MusicLocation) {
        history.add(location.copy(returnSection=focusSection));location=next;focusSection=0
        scope.launch { rows.scrollToItem(0);withFrameNanos { };runCatching { playlistFocus.requestFocus() } }
    }
    fun back() {
        if(history.isNotEmpty()) location=history.removeAt(history.lastIndex)
        else location=MusicLocation()
        val section=location.returnSection
        scope.launch {
            rows.scrollToItem(if(section==2) 1 else 0);withFrameNanos { }
            runCatching { (if(section==2) channelFocus else if(section==1) playlistFocus else heroFocus).requestFocus() }
        }
    }
    BackHandler(true) {
        if(focusSection!=0 && location.page==MusicPage.HOME && history.isEmpty()) {
            scope.launch { rows.animateScrollToItem(0);heroFocus.requestFocus() }
        } else if(location.page==MusicPage.HOME && history.isEmpty()) onBackRoot() else back()
    }
    LaunchedEffect(queueRequest) {
        if(queueRequest!=openQueueObserved && queue.isNotEmpty()) {
            openQueueObserved=queueRequest
            navigate(MusicLocation(MusicPage.QUEUE,"Playing queue",tracks=queue))
            rows.scrollToItem(0)
        }
    }
    fun openCard(card:MusicHomeCard,playAll:Boolean=false) {
        if(busy) return
        if(card.browseId==null && card.playlistId==null && card.videoId!=null) {
            onPlay(listOf(MusicTrack(card.videoId,card.title,card.thumbnail)),0);return
        }
        model.homeCard(card) { loaded ->
            if(loaded.tracks.isNotEmpty()) {
                if(playAll) onPlay(loaded.tracks,0)
                else navigate(MusicLocation(MusicPage.DETAIL,card.title,card.thumbnail,tracks=loaded.tracks))
            } else if(loaded.shelves.isNotEmpty()) navigate(MusicLocation(title=card.title,image=card.thumbnail,shelves=loaded.shelves))
        }
    }
    fun sectionKeys(down:Pair<Int,FocusRequester>?,up:Pair<Int,FocusRequester>?):Modifier = Modifier.onPreviewKeyEvent { event ->
        val target=when(event.key) { Key.DirectionDown -> down;Key.DirectionUp -> up;else -> null }
        if(target==null) false else {
            if(event.type==KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount==0) scope.launch {
                if(target.first>=0) rows.animateScrollToItem(target.first)
                withFrameNanos { }
                runCatching { target.second.requestFocus() }
            }
            true
        }
    }
    val shelves=location.shelves.ifEmpty { state.shelves }
    val featured=shelves.flatMap { it.cards }.firstOrNull { it.playlistId!=null || it.browseId?.startsWith("VL")==true }
    val firstShelf=shelves.firstOrNull { shelf -> shelf.cards.any { it.browseId?.startsWith("UC")!=true } }
    val channels=(state.artists+shelves.flatMap { it.cards }.filter { it.browseId?.startsWith("UC")==true }).distinctBy { it.browseId }
    val detail=location.page==MusicPage.DETAIL || location.page==MusicPage.QUEUE
    val detailTracks=if(location.page==MusicPage.QUEUE) queue else location.tracks
    Column(Modifier.fillMaxSize().padding(start=36.dp,end=36.dp,top=28.dp,bottom=if(hasPlayer) 82.dp else 22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().height(134.dp),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    MusicIcon(R.drawable.ic_nav_music,"",22)
                    Text("YouTube Music",style=MaterialTheme.typography.titleMedium)
                    Text(state.browserProfile ?: state.channel?.title.orEmpty(),color=Color(0xFFABBDD9),style=MaterialTheme.typography.labelSmall,maxLines=1)
                }
                Text(if(detail) location.title else location.title.ifBlank { "Your music. Your way." },style=MaterialTheme.typography.headlineMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(if(detail) "${detailTracks.size} tracks · Select a track to play" else "Your mixes, playlists and channels, all in one place.",color=Color(0xFFB9C7DD),style=MaterialTheme.typography.bodyMedium)
                Row(Modifier.focusRequester(heroFocus).focusGroup().then(sectionKeys(0 to playlistFocus,null)).onFocusChanged { if(it.hasFocus) focusSection=0 },horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Button(onClick={
                        if(detail && detailTracks.isNotEmpty()) onPlay(detailTracks,0)
                        else featured?.let { openCard(it,true) } ?: state.playlists.firstOrNull()?.let { playlist -> model.playlist(playlist) { onPlay(it.tracks,0) } }
                    },enabled=!busy && (detailTracks.isNotEmpty() || featured!=null || state.playlists.isNotEmpty()),modifier=Modifier.railBoundary()) {
                        MusicIcon(R.drawable.ic_media_play,"",18);Spacer(Modifier.width(6.dp));Text(if(detail) "Play all" else "Play my mix")
                    }
                    Button(onClick={ navigate(MusicLocation(MusicPage.LIBRARY,"Your library")) }) { MusicIcon(R.drawable.ic_nav_apps,"",18);Spacer(Modifier.width(6.dp));Text("Browse library") }
                    if(!hasPlayer) Button(onClick={ context.startActivity(Intent(context,MusicSettingsActivity::class.java)) }) { MusicIcon(R.drawable.ic_settings,"Music settings",18) }
                }
            }
            // A stable space for the live player. The WebView itself lives outside this scrollable browser.
            if(hasPlayer) Spacer(Modifier.width(280.dp))
            else Box(Modifier.width(228.dp).fillMaxHeight().clip(RoundedCornerShape(22.dp)).background(Color(0xFF202A4A))) {
                AsyncImage(featured?.thumbnail,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0x9910192E),Color.Transparent))))
                Text("MUSIC",Modifier.align(Alignment.BottomEnd).padding(16.dp),color=Color.White.copy(alpha=0.6f),style=MaterialTheme.typography.titleLarge)
            }
        }
        if(busy) Text("Loading…",style=MaterialTheme.typography.labelMedium,color=Color(0xFFABC5FF))
        (message ?: state.message)?.takeUnless { it=="Music browser connected." }?.let { Text(it,style=MaterialTheme.typography.labelMedium) }
        if(!state.authorized) Button(onClick={ context.startActivity(Intent(context,MusicSettingsActivity::class.java)) }) { Text("Connect Music in settings") }
        LazyColumn(state=rows,modifier=Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(18.dp),contentPadding=PaddingValues(6.dp)) {
            if(detail) {
                itemsIndexed(detailTracks,key={ index,track -> "${track.id}:$index" }) { index,track ->
                    Surface(onClick={ onPlay(detailTracks,index) },modifier=Modifier.fillMaxWidth().railBoundary().then(if(index==0) Modifier.focusRequester(playlistFocus).railBoundary().then(sectionKeys(null,-1 to heroFocus)) else Modifier).onFocusChanged { if(it.hasFocus) focusSection=1 },
                        colors=ClickableSurfaceDefaults.colors(containerColor=if(queue.getOrNull(queueIndex)?.id==track.id) Color(0xFF253C60) else Color(0x66172439),focusedContainerColor=Color(0xFFCDDFFF),focusedContentColor=Color(0xFF0B1729))) {
                        Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            Text("${index+1}",style=MaterialTheme.typography.labelLarge,modifier=Modifier.width(28.dp))
                            AsyncImage(track.thumbnail,null,Modifier.width(90.dp).height(50.dp).clip(RoundedCornerShape(8.dp)),contentScale=ContentScale.Crop)
                            Text(track.title,Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyLarge)
                            if(queue.getOrNull(queueIndex)?.id==track.id) MusicIcon(R.drawable.ic_nav_music,"Now playing",20)
                        }
                    }
                }
            } else {
                item(key="playlists") {
                    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text(if(location.page==MusicPage.LIBRARY) "Your playlists" else firstShelf?.title ?: "Your playlists",style=MaterialTheme.typography.titleMedium)
                        LazyRow(Modifier.focusGroup().then(sectionKeys(1 to channelFocus,-1 to heroFocus)).onFocusChanged { if(it.hasFocus) focusSection=1 },horizontalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(4.dp)) {
                            if(location.page==MusicPage.LIBRARY || firstShelf==null) {
                                itemsIndexed(state.playlists,key={ _,p -> p.id }) { index,p ->
                                    MusicArtwork(p.title,p.thumbnail,"${p.count} tracks",if(index==0) Modifier.focusRequester(playlistFocus).railBoundary() else Modifier) {
                                        model.playlist(p) { loaded -> navigate(MusicLocation(MusicPage.DETAIL,p.title,p.thumbnail,tracks=loaded.tracks)) }
                                    }
                                }
                            } else itemsIndexed(firstShelf.cards,key={ index,c -> "${c.browseId}:${c.playlistId}:${c.videoId}:$index" }) { index,c ->
                                MusicArtwork(c.title,c.thumbnail,"Mix · Playlist",if(index==0) Modifier.focusRequester(playlistFocus).railBoundary() else Modifier) { openCard(c) }
                            }
                            if((location.page==MusicPage.LIBRARY || firstShelf==null) && state.playlists.isEmpty()) item { Button(onClick=model::refresh,modifier=Modifier.focusRequester(playlistFocus).railBoundary()) { Text("Refresh library") } }
                        }
                    }
                }
                item(key="channels") {
                    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text("Your channels & artists",style=MaterialTheme.typography.titleMedium)
                        LazyRow(Modifier.focusGroup().then(sectionKeys(null,0 to playlistFocus)).onFocusChanged { if(it.hasFocus) focusSection=2 },horizontalArrangement=Arrangement.spacedBy(22.dp),contentPadding=PaddingValues(6.dp)) {
                            itemsIndexed(channels,key={ index,c -> "${c.browseId}:$index" }) { index,c ->
                                Column(Modifier.width(90.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                    Surface(onClick={ openCard(c) },modifier=Modifier.panelFocus().size(70.dp).then(if(index==0) Modifier.focusRequester(channelFocus).railBoundary() else Modifier),shape=ClickableSurfaceDefaults.shape(CircleShape)) {
                                        AsyncImage(c.thumbnail,c.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                                    }
                                    Text(c.title,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelMedium)
                                }
                            }
                            if(channels.isEmpty()) item {
                                Button(onClick=model::refresh,enabled=!busy,modifier=Modifier.focusRequester(channelFocus).railBoundary()) { Text("Refresh channels") }
                                Text("Follow artists in YouTube Music to add them here.",style=MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                if(location.page!=MusicPage.LIBRARY) items(shelves.filter { it!==firstShelf },key={ it.title }) { shelf ->
                    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text(shelf.title,style=MaterialTheme.typography.titleMedium)
                        LazyRow(horizontalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(4.dp)) {
                            items(shelf.cards) { c -> MusicArtwork(c.title,c.thumbnail,"YouTube Music") { openCard(c) } }
                        }
                    }
                }
                item { Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Button(onClick=model::refresh,enabled=!busy) { Text("Refresh Music") }
                    Button(onClick={ context.startActivity(Intent(context,MusicSettingsActivity::class.java)) }) { Text("Music settings") }
                } }
            }
        }
    }
}

@Composable
private fun MusicArtwork(title:String,image:String?,subtitle:String,modifier:Modifier=Modifier,onClick:()->Unit) {
    Surface(onClick=onClick,modifier=modifier.panelFocus().width(204.dp).height(126.dp).focusPop(),shape=ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),scale=ClickableSurfaceDefaults.scale(focusedScale=1f),
        colors=ClickableSurfaceDefaults.colors(containerColor=Color(0xFF20304A),focusedContainerColor=Color(0xFF314D7A))) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(image,title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color(0xED071020)))))
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(title,maxLines=1,overflow=TextOverflow.Ellipsis,color=Color.White,style=MaterialTheme.typography.bodyLarge,fontWeight=FontWeight.SemiBold)
                Text(subtitle,color=Color(0xFFB7C8E2),style=MaterialTheme.typography.labelSmall,maxLines=1)
            }
        }
    }
}

@Composable
internal fun MusicIcon(icon:Int,label:String,size:Int=22) {
    Image(painterResource(icon),label.takeIf { it.isNotBlank() },Modifier.size(size.dp),colorFilter=ColorFilter.tint(LocalContentColor.current))
}

@Composable
fun MusicPlayerBar(track:MusicTrack,isPlaying:Boolean,canPrevious:Boolean,canNext:Boolean,queueSize:Int,
    onPrevious:()->Unit,onToggle:()->Unit,onNext:()->Unit,onQueue:()->Unit,onPhotos:()->Unit,modifier:Modifier=Modifier) {
    Row(modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xF0101C30)).padding(horizontal=14.dp,vertical=8.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        AsyncImage(track.thumbnail,null,Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),contentScale=ContentScale.Crop)
        Column(Modifier.weight(1f)) {
            Text(track.title,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyLarge)
            Text(if(isPlaying) "Now playing" else "Paused",color=Color(0xFFADBED9),style=MaterialTheme.typography.labelSmall)
        }
        Button(onClick=onPrevious,enabled=canPrevious,contentPadding=PaddingValues(10.dp),modifier=Modifier.railBoundary()) { MusicIcon(R.drawable.ic_media_previous,"Previous track") }
        Button(onClick=onToggle,contentPadding=PaddingValues(10.dp),modifier=if(!canPrevious) Modifier.railBoundary() else Modifier) { MusicIcon(if(isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play,if(isPlaying) "Pause" else "Play") }
        Button(onClick=onNext,enabled=canNext,contentPadding=PaddingValues(10.dp)) { MusicIcon(R.drawable.ic_media_next,"Next track") }
        Button(onClick=onQueue) { MusicIcon(R.drawable.ic_media_queue,"");Spacer(Modifier.width(6.dp));Text("Queue · $queueSize") }
        Button(onClick=onPhotos) { MusicIcon(R.drawable.ic_nav_photos,"");Spacer(Modifier.width(6.dp));Text("Photos") }
    }
}
