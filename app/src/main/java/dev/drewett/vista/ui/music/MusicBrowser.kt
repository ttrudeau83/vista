package dev.drewett.vista.ui.music

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import dev.drewett.vista.ui.components.VistaButton as Button
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

/** One horizontal row on the Music home/library; [first] goes on its first item (focus target + rail edge). */
private class MusicRow(val key:String,val title:String,val subtitle:String?=null,val content:@Composable (first:Modifier)->Unit)

private const val YOUTUBE_MUSIC_PACKAGE="com.google.android.youtube.tvmusic"

/** Browsing has its own history; opening another playlist never replaces the playing queue. */
@Composable
internal fun MusicBrowser(model:MusicViewModel,contentFocus:FocusRequester,hasPlayer:Boolean,
    queue:List<MusicTrack>,queueIndex:Int,queueRequest:Int,onPlay:(List<MusicTrack>,Int)->Unit,onPhotos:()->Unit,onBackRoot:()->Unit,navigation:MusicBrowserState) {
    val state by model.state.collectAsState()
    val busy by model.busy.collectAsState()
    val message by model.message.collectAsState()
    val context=LocalContext.current
    var location by navigation.location
    val history=navigation.history
    val rows=navigation.rows
    val heroFocus=contentFocus
    val trackFocus=remember { FocusRequester() }
    val scope=rememberCoroutineScope()
    // 0 = header buttons, n = row n-1 (or the track list on a detail page).
    var focusSection by navigation.focusSection
    var openQueueObserved by remember { mutableStateOf(queueRequest) }
    val rowFocusHolder=remember { mutableStateOf<List<FocusRequester>>(emptyList()) }
    val youTubeMusic=remember { context.packageManager.getLeanbackLaunchIntentForPackage(YOUTUBE_MUSIC_PACKAGE)
        ?: context.packageManager.getLaunchIntentForPackage(YOUTUBE_MUSIC_PACKAGE) }
    LaunchedEffect(Unit) { model.refreshPersonal() }

    val detail=location.page==MusicPage.DETAIL || location.page==MusicPage.QUEUE
    fun focusContent(section:Int,isDetail:Boolean) {
        scope.launch {
            if(section>0) rows.scrollToItem(if(isDetail) 0 else section-1)
            withFrameNanos { }
            val target=if(section<=0) heroFocus else if(isDetail) trackFocus else rowFocusHolder.value.getOrNull(section-1)
            runCatching { (target ?: heroFocus).requestFocus() }
        }
    }
    fun navigate(next:MusicLocation) {
        history.add(location.copy(returnSection=focusSection));location=next;focusSection=1
        focusContent(1,next.page==MusicPage.DETAIL || next.page==MusicPage.QUEUE)
    }
    fun back() {
        location=if(history.isNotEmpty()) history.removeAt(history.lastIndex) else MusicLocation()
        focusSection=location.returnSection
        focusContent(location.returnSection,location.page==MusicPage.DETAIL || location.page==MusicPage.QUEUE)
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
    fun openYouTubeMusic() { youTubeMusic?.let { runCatching { context.startActivity(Intent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } }
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

    val nested=location.page==MusicPage.HOME && location.shelves.isNotEmpty()
    val shelves=location.shelves.ifEmpty { state.shelves }
    val featured=shelves.flatMap { it.cards }.firstOrNull { it.playlistId!=null || it.browseId?.startsWith("VL")==true }
    val firstShelf=shelves.firstOrNull { shelf -> shelf.cards.any { it.browseId?.startsWith("UC")!=true } }
    val channels=(state.artists+shelves.flatMap { it.cards }.filter { it.browseId?.startsWith("UC")==true }).distinctBy { it.browseId }
    val detailTracks=if(location.page==MusicPage.QUEUE) queue else location.tracks
    val profile=state.browserProfile ?: state.channel?.title.orEmpty()

    @Composable fun cardRow(cards:List<MusicHomeCard>,subtitle:String,first:Modifier) {
        LazyRow(horizontalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(4.dp)) {
            itemsIndexed(cards,key={ index,c -> "${c.browseId}:${c.playlistId}:${c.videoId}:$index" }) { index,c ->
                MusicArtwork(c.title,c.thumbnail,subtitle,if(index==0) first else Modifier) { openCard(c) }
            }
        }
    }
    val playlistsRow=MusicRow("playlists","Your playlists","${state.playlists.size} playlists") { first ->
        LazyRow(horizontalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(4.dp)) {
            itemsIndexed(state.playlists,key={ _,p -> p.id }) { index,p ->
                MusicArtwork(p.title,p.thumbnail,"${p.count} tracks",if(index==0) first else Modifier) {
                    model.playlist(p) { loaded -> navigate(MusicLocation(MusicPage.DETAIL,p.title,p.thumbnail,tracks=loaded.tracks)) }
                }
            }
            if(state.playlists.isEmpty()) item { Button(onClick=model::refresh,enabled=!busy,modifier=first) { Text("Refresh library") } }
        }
    }
    val historyRow=MusicRow("history","Recently played","Pick up where you left off") { first ->
        LazyRow(horizontalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(4.dp)) {
            itemsIndexed(state.history.take(30),key={ index,t -> "${t.id}:$index" }) { index,t ->
                MusicArtwork(t.title,t.thumbnail,if(queue.getOrNull(queueIndex)?.id==t.id) "Now playing" else "Played recently",if(index==0) first else Modifier) { onPlay(state.history,index) }
            }
            item(key="history-all") { MoreTile("All history") { navigate(MusicLocation(MusicPage.DETAIL,"Recently played",tracks=state.history)) } }
        }
    }
    val podcastsRow=MusicRow("podcasts","Podcasts","Saved in your library") { first -> cardRow(state.podcasts,"Podcast",first) }
    val channelsRow=MusicRow("channels","Your channels & artists") { first ->
        LazyRow(horizontalArrangement=Arrangement.spacedBy(22.dp),contentPadding=PaddingValues(6.dp)) {
            itemsIndexed(channels,key={ index,c -> "${c.browseId}:$index" }) { index,c ->
                Column(Modifier.width(96.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Surface(onClick={ openCard(c) },modifier=Modifier.panelFocus().size(76.dp).focusPop(shape=CircleShape).then(if(index==0) first else Modifier),
                        shape=ClickableSurfaceDefaults.shape(CircleShape),scale=ClickableSurfaceDefaults.scale(focusedScale=1f)) {
                        AsyncImage(c.thumbnail,c.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                    }
                    Text(c.title,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelMedium)
                }
            }
            if(channels.isEmpty()) item { Button(onClick=model::refresh,enabled=!busy,modifier=first) { Text("Refresh channels") } }
        }
    }
    val footerRow=MusicRow("footer","More") { first ->
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Button(onClick=model::refresh,enabled=!busy,modifier=first) { Text("Refresh Music") }
            Button(onClick={ context.startActivity(Intent(context,MusicSettingsActivity::class.java)) }) { Text("Music settings") }
        }
    }
    val sections:List<MusicRow> = when {
        detail -> emptyList()
        nested -> shelves.map { shelf -> MusicRow("shelf:"+shelf.title,shelf.title) { first -> cardRow(shelf.cards,"YouTube Music",first) } }
        location.page==MusicPage.LIBRARY -> buildList {
            add(playlistsRow)
            if(state.podcasts.isNotEmpty()) add(podcastsRow)
            if(state.history.isNotEmpty()) add(historyRow)
            add(channelsRow)
        }
        else -> buildList {
            if(state.history.isNotEmpty()) add(historyRow)
            if(firstShelf!=null) add(MusicRow("mixes",firstShelf.title,"Made for you") { first -> cardRow(firstShelf.cards,"Mix · Playlist",first) })
            else add(playlistsRow)
            if(state.podcasts.isNotEmpty()) add(podcastsRow)
            add(channelsRow)
            shelves.filter { it!==firstShelf }.forEach { shelf -> add(MusicRow("shelf:"+shelf.title,shelf.title) { first -> cardRow(shelf.cards,"YouTube Music",first) }) }
            add(footerRow)
        }
    }
    val rowFocus=remember(sections.map { it.key }) { List(sections.size) { FocusRequester() } }
    rowFocusHolder.value=rowFocus
    // Rows that load late (history, podcasts) insert above the visible ones; LazyColumn would keep the old
    // first row pinned and hide the new one. While the header has focus, show the list from its top.
    LaunchedEffect(sections.firstOrNull()?.key) { if(focusSection==0 && !detail) rows.scrollToItem(0) }

    Column(Modifier.fillMaxSize().padding(start=36.dp,end=36.dp,top=28.dp,bottom=if(hasPlayer) 82.dp else 22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().height(140.dp),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    MusicIcon(R.drawable.ic_nav_music,"",20)
                    Text("YouTube Music",style=MaterialTheme.typography.titleSmall)
                    if(profile.isNotBlank()) Text("· $profile",color=MaterialTheme.colorScheme.onBackground.copy(alpha=0.7f),style=MaterialTheme.typography.labelMedium,maxLines=1)
                }
                Text(when { detail || nested -> location.title; location.page==MusicPage.LIBRARY -> "Your library"; else -> "Music" },
                    style=MaterialTheme.typography.headlineMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(when {
                        detail -> "${detailTracks.size} ${if(detailTracks.size==1) "track" else "tracks"} · Select one to play from there"
                        location.page==MusicPage.LIBRARY -> "Playlists, podcasts, history and artists"
                        nested -> "From YouTube Music"
                        else -> "Your history, mixes, podcasts and artists"
                    },color=MaterialTheme.colorScheme.onBackground.copy(alpha=0.7f),style=MaterialTheme.typography.bodyMedium,maxLines=1)
                // Header actions scroll sideways (focus brings each into view) so they never clip beside the player.
                Row(Modifier.focusRequester(heroFocus).focusGroup().horizontalScroll(rememberScrollState())
                    .then(sectionKeys(0 to (if(detail) trackFocus else rowFocus.firstOrNull() ?: heroFocus),null))
                    .onFocusChanged { if(it.hasFocus) focusSection=0 },horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Button(onClick={
                        if(detail && detailTracks.isNotEmpty()) onPlay(detailTracks,0)
                        else featured?.let { openCard(it,true) } ?: state.playlists.firstOrNull()?.let { playlist -> model.playlist(playlist) { onPlay(it.tracks,0) } }
                    },enabled=!busy && (detailTracks.isNotEmpty() || featured!=null || state.playlists.isNotEmpty()),modifier=Modifier.railBoundary()) {
                        MusicIcon(R.drawable.ic_media_play,"",18);Spacer(Modifier.width(6.dp));Text(if(detail) "Play all" else "Play my mix")
                    }
                    if(location.page!=MusicPage.LIBRARY) Button(onClick={ navigate(MusicLocation(MusicPage.LIBRARY,"Your library")) }) { MusicIcon(R.drawable.ic_nav_apps,"",18);Spacer(Modifier.width(6.dp));Text("Library") }
                    if(state.history.isNotEmpty() && location.tracks!==state.history) Button(onClick={ navigate(MusicLocation(MusicPage.DETAIL,"Recently played",tracks=state.history)) }) {
                        MusicIcon(R.drawable.ic_media_queue,"",18);Spacer(Modifier.width(6.dp));Text("History") }
                    if(youTubeMusic!=null) Button(onClick=::openYouTubeMusic) { MusicIcon(R.drawable.ic_nav_music,"",18);Spacer(Modifier.width(6.dp));Text("Open YouTube Music") }
                    Button(onClick=onPhotos) { MusicIcon(R.drawable.ic_nav_photos,"",18);Spacer(Modifier.width(6.dp));Text("Photos") }
                    Button(onClick={ context.startActivity(Intent(context,MusicSettingsActivity::class.java)) }) { MusicIcon(R.drawable.ic_settings,"Music settings",18) }
                }
            }
            // A stable space for the live player. The WebView itself lives outside this scrollable browser.
            if(hasPlayer) Spacer(Modifier.width(280.dp))
            else Box(Modifier.width(228.dp).fillMaxHeight().clip(RoundedCornerShape(22.dp)).background(Color(0xFF202A4A))) {
                AsyncImage(location.image ?: state.history.firstOrNull()?.thumbnail ?: featured?.thumbnail,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0x9910192E),Color.Transparent))))
            }
        }
        if(busy) Text("Loading…",style=MaterialTheme.typography.labelMedium,color=Color(0xFFABC5FF))
        (message ?: state.message ?: state.homeMessage)?.takeUnless { it=="Music browser connected." }?.let { Text(it,style=MaterialTheme.typography.labelMedium,maxLines=2) }
        if(!state.authorized) Button(onClick={ context.startActivity(Intent(context,MusicSettingsActivity::class.java)) }) { Text("Connect Music in settings") }
        LazyColumn(state=rows,modifier=Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(20.dp),contentPadding=PaddingValues(6.dp)) {
            if(detail) {
                itemsIndexed(detailTracks,key={ index,track -> "${track.id}:$index" }) { index,track ->
                    val current=queue.getOrNull(queueIndex)?.id==track.id
                    Surface(onClick={ onPlay(detailTracks,index) },modifier=Modifier.fillMaxWidth().railBoundary().focusPop()
                        .then(if(index==0) Modifier.focusRequester(trackFocus).then(sectionKeys(null,-1 to heroFocus)) else Modifier)
                        .onFocusChanged { if(it.hasFocus) focusSection=1 },scale=ClickableSurfaceDefaults.scale(focusedScale=1f),
                        colors=ClickableSurfaceDefaults.colors(containerColor=if(current) Color(0xFF253C60) else Color(0xEE172439),contentColor=Color.White,focusedContainerColor=Color(0xFFCDDFFF),focusedContentColor=Color(0xFF0B1729))) {
                        Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            Text("${index+1}",style=MaterialTheme.typography.labelLarge,modifier=Modifier.width(28.dp))
                            AsyncImage(track.thumbnail,null,Modifier.width(90.dp).height(50.dp).clip(RoundedCornerShape(8.dp)),contentScale=ContentScale.Crop)
                            Text(track.title,Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyLarge)
                            if(current) MusicIcon(R.drawable.ic_nav_music,"Now playing",20)
                        }
                    }
                }
                if(detailTracks.isEmpty()) item { Text("Nothing to show here yet.",style=MaterialTheme.typography.bodyLarge) }
            } else {
                itemsIndexed(sections,key={ _,row -> row.key }) { index,row ->
                    val down=rowFocus.getOrNull(index+1)?.let { (index+1) to it }
                    val up=if(index==0) (-1 to heroFocus) else (index-1) to rowFocus[index-1]
                    Column(Modifier.focusGroup().then(sectionKeys(down,up)).onFocusChanged { if(it.hasFocus) focusSection=index+1 },
                        verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text(row.title,style=MaterialTheme.typography.titleMedium)
                            row.subtitle?.let { Text(it,color=MaterialTheme.colorScheme.onBackground.copy(alpha=0.6f),style=MaterialTheme.typography.labelMedium) }
                        }
                        row.content(Modifier.focusRequester(rowFocus[index]).railBoundary())
                    }
                }
                if(sections.isEmpty() && state.authorized) item { Text(if(busy) "Loading your music…" else "Nothing to show yet. Try Refresh Music.",style=MaterialTheme.typography.bodyLarge) }
            }
        }
    }
}

@Composable
private fun MoreTile(label:String,onClick:()->Unit) {
    Surface(onClick=onClick,modifier=Modifier.panelFocus().width(150.dp).height(126.dp).focusPop(),shape=ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        scale=ClickableSurfaceDefaults.scale(focusedScale=1f),colors=ClickableSurfaceDefaults.colors(containerColor=Color(0xFF1A2740),focusedContainerColor=Color(0xFF314D7A))) {
        Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
            MusicIcon(R.drawable.ic_media_queue,"",26);Spacer(Modifier.height(8.dp))
            Text(label,color=Color.White,style=MaterialTheme.typography.labelLarge)
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
    onPrevious:()->Unit,onToggle:()->Unit,onNext:()->Unit,onQueue:()->Unit,modifier:Modifier=Modifier) {
    Row(modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xF0101C30)).padding(horizontal=14.dp,vertical=8.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        AsyncImage(track.thumbnail,null,Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),contentScale=ContentScale.Crop)
        Column(Modifier.weight(1f)) {
            Text(track.title,color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyLarge)
            Text(if(isPlaying) "Now playing" else "Paused",color=Color(0xFFADBED9),style=MaterialTheme.typography.labelSmall)
        }
        Button(onClick=onPrevious,enabled=canPrevious,contentPadding=PaddingValues(10.dp),modifier=Modifier.railBoundary()) { MusicIcon(R.drawable.ic_media_previous,"Previous track") }
        Button(onClick=onToggle,contentPadding=PaddingValues(10.dp),modifier=if(!canPrevious) Modifier.railBoundary() else Modifier) { MusicIcon(if(isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play,if(isPlaying) "Pause" else "Play") }
        Button(onClick=onNext,enabled=canNext,contentPadding=PaddingValues(10.dp)) { MusicIcon(R.drawable.ic_media_next,"Next track") }
        Button(onClick=onQueue) { MusicIcon(R.drawable.ic_media_queue,"");Spacer(Modifier.width(6.dp));Text("Queue · $queueSize") }
    }
}
