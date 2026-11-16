package dev.drewett.vista.ui.music

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.drewett.vista.ui.components.VistaButton as Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.drewett.vista.MusicSettingsActivity
import dev.drewett.vista.data.music.MusicTrack
import dev.drewett.vista.ui.photos.AmbientPhotoView
import dev.drewett.vista.ui.theme.vistaBackdrop
import kotlinx.coroutines.delay

@Composable
fun MusicScreen(contentFocus:FocusRequester,onAmbientChanged:(Boolean)->Unit={},model:MusicViewModel=viewModel(),onRequestTopBar:()->Unit={}) {
    val context=LocalContext.current
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    var playbackQueue by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var playbackIndex by remember { mutableStateOf(-1) }
    val playing=playbackQueue.getOrNull(playbackIndex)
    var isPlaying by remember { mutableStateOf(true) }
    var queueRequest by remember { mutableStateOf(0) }
    val playerController=remember { YouTubePlaybackController() }
    val browserNavigation=remember { MusicBrowserState() }
    fun skip(delta:Int) {
        MusicQueuePolicy.move(playbackQueue.size,playbackIndex,delta)?.let { playerController.reset();playbackIndex=it;isPlaying=true }
    }
    fun togglePlayback() { if(isPlaying) playerController.pause() else playerController.play();isPlaying=!isPlaying }
    var ambient by remember { mutableStateOf(false) }
    var hideVideo by remember { mutableStateOf(false) }

    var overlayAwake by remember { mutableStateOf(true) }
    var overlayInteraction by remember { mutableStateOf(0) }
    var revealRelease by remember { mutableStateOf(false) }
    val overlayAlpha by animateFloatAsState(if(overlayAwake) 1f else 0f,tween(650),label="music overlay")
    val ambientExitFocus=remember { FocusRequester() }
    val nextTrack=rememberUpdatedState<()->Unit> {
        if(playbackIndex<playbackQueue.lastIndex) skip(1) else isPlaying=false
    }
    val trackPlayer=playing?.let { track ->
        remember(track.id,playbackIndex) { movableContentOf<Modifier,Boolean> { playerModifier,interactive -> OfficialYouTubePlayer(track.id,playerModifier,interactive,onEnded={ nextTrack.value() },controller=playerController,onPlayingChanged={ isPlaying=it }) } }
    }
    LaunchedEffect(ambient) { onAmbientChanged(ambient);if(ambient) runCatching { ambientExitFocus.requestFocus() } }
    DisposableEffect(Unit) { onDispose { onAmbientChanged(false) } }
    LaunchedEffect(ambient,overlayInteraction) {
        overlayAwake=true
        if(ambient) { delay(4000);overlayAwake=false }
    }
    LaunchedEffect(state.authorized) {
        if(!state.authorized) { ambient=false;playbackQueue=emptyList();playbackIndex=-1 }
        else if(state.playlists.isEmpty()) model.refresh()
    }
    BackHandler(enabled=ambient) { ambient=false }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
    if(ambient && playing!=null && trackPlayer!=null) {
        Box(Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent { event ->
            if(event.type==KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount==0) {
                if(event.nativeKeyEvent.keyCode in setOf(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,android.view.KeyEvent.KEYCODE_MEDIA_NEXT,android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)) {
                    overlayAwake=true;overlayInteraction++
                }
                when(event.nativeKeyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { togglePlayback();return@onPreviewKeyEvent true }
                    android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> { skip(1);return@onPreviewKeyEvent true }
                    android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { skip(-1);return@onPreviewKeyEvent true }
                }
            }
            val select=event.key==Key.DirectionCenter || event.key==Key.Enter || event.key==Key.NumPadEnter
            if(event.type==KeyEventType.KeyUp && select && revealRelease) { revealRelease=false;true }
            else if(event.type==KeyEventType.KeyDown) {
                val wasFaded=!overlayAwake
                overlayAwake=true;overlayInteraction++
                val direction=event.key in setOf(Key.DirectionLeft,Key.DirectionRight,Key.DirectionUp,Key.DirectionDown)
                if(select && (wasFaded || revealRelease)) { revealRelease=true;true } else wasFaded && direction
            } else false
        }) {
            MusicAmbientPhotos(Modifier.fillMaxSize().zIndex(if(hideVideo) 1f else -1f)) { ambient=false }
            Box(Modifier.align(Alignment.BottomEnd).padding(end=24.dp,bottom=20.dp)) {
                trackPlayer(Modifier.width(224.dp).height(126.dp),!hideVideo)
            }
            Column(Modifier.align(Alignment.BottomEnd).padding(end=24.dp,bottom=if(hideVideo) 24.dp else 162.dp).width(320.dp).zIndex(2f).graphicsLayer { alpha=overlayAlpha },
                horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(8.dp)) {

                Text(playing!!.title,color=Color.White.copy(alpha=0.85f),maxLines=1,overflow=TextOverflow.Ellipsis,textAlign=TextAlign.End,
                    modifier=Modifier.fillMaxWidth(),style=MaterialTheme.typography.titleMedium.copy(shadow=Shadow(Color.Black.copy(alpha=0.6f),Offset(0f,1f),3f)))
                val buttonColors=ButtonDefaults.colors(containerColor=Color.White.copy(alpha=0.04f),contentColor=Color.White,
                    focusedContainerColor=Color.White.copy(alpha=0.18f),focusedContentColor=Color.White)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Button(onClick={ skip(-1) },enabled=playbackIndex>0,colors=buttonColors,contentPadding=androidx.compose.foundation.layout.PaddingValues(10.dp)) { MusicIcon(dev.drewett.vista.R.drawable.ic_media_previous,"Previous track") }
                    Button(onClick=::togglePlayback,colors=buttonColors,contentPadding=androidx.compose.foundation.layout.PaddingValues(10.dp)) { MusicIcon(if(isPlaying) dev.drewett.vista.R.drawable.ic_media_pause else dev.drewett.vista.R.drawable.ic_media_play,if(isPlaying) "Pause" else "Play") }
                    Button(onClick={ skip(1) },enabled=playbackIndex<playbackQueue.lastIndex,colors=buttonColors,contentPadding=androidx.compose.foundation.layout.PaddingValues(10.dp)) { MusicIcon(dev.drewett.vista.R.drawable.ic_media_next,"Next track") }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick={ hideVideo=!hideVideo },colors=buttonColors) { Text(if(hideVideo) "Show video" else "Hide video") }
                    Button(onClick={ ambient=false },colors=buttonColors,modifier=Modifier.focusRequester(ambientExitFocus)) { Text("Return to music") }
                }
            }
        }
    } else {
        Box(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if(event.type!=KeyEventType.KeyDown || event.nativeKeyEvent.repeatCount!=0) false
            else when(event.nativeKeyEvent.keyCode) {
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { togglePlayback();true }
                android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> { skip(1);true }
                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { skip(-1);true }
                else -> false
            }
        }) {
            MusicBrowser(model,contentFocus,playing!=null,playbackQueue,playbackIndex,queueRequest,
                onPhotos={ if(playing!=null) ambient=true else context.startActivity(android.content.Intent(context,dev.drewett.vista.PhotoSlideshowActivity::class.java)) },
                onPlay={ tracks,index -> if(tracks.isNotEmpty()) { playerController.reset();playbackQueue=tracks.toList();playbackIndex=index;isPlaying=true } },onBackRoot=onRequestTopBar,navigation=browserNavigation)
            if(playing!=null && trackPlayer!=null) {
                Box(Modifier.align(Alignment.TopEnd).padding(top=28.dp,end=36.dp)) {
                    trackPlayer(Modifier.width(280.dp).height(150.dp),true)
                }
                MusicPlayerBar(playing,isPlaying,playbackIndex>0,playbackIndex<playbackQueue.lastIndex,playbackQueue.size,
                    onPrevious={ skip(-1) },onToggle=::togglePlayback,onNext={ skip(1) },onQueue={ queueRequest++ },
                    modifier=Modifier.align(Alignment.BottomCenter).padding(horizontal=30.dp,vertical=8.dp))
            }
        }
    }
    }
}

@Composable
private fun MusicAmbientPhotos(modifier:Modifier,exit:()->Unit) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val view=remember { AmbientPhotoView(context,allowMusic=true,exit=exit) }
    DisposableEffect(view,lifecycle) {
        val observer=LifecycleEventObserver { _,event ->
            when(event) {
                Lifecycle.Event.ON_START -> view.start()
                Lifecycle.Event.ON_STOP -> view.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) view.start()
        onDispose { lifecycle.removeObserver(observer);view.stop() }
    }
    AndroidView(factory={ view },modifier=modifier)
}

@Composable
fun MusicSettingsScreen(onDone:()->Unit,model:MusicViewModel=viewModel(),onRequestTopBar:()->Unit={}) {
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    var clientId by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    val doneFocus=remember { FocusRequester() }
    LaunchedEffect(Unit) { doneFocus.requestFocus() }
    BackHandler { secret="";onDone() }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
    LazyColumn(Modifier.fillMaxSize().vistaBackdrop().padding(30.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                Text("Music account",modifier=Modifier.weight(1f),style=MaterialTheme.typography.headlineMedium)
                Button(onClick={ secret="";onDone() },modifier=Modifier.focusRequester(doneFocus)) { Text("Done") }
            }
        }
        item { Text("Enable YouTube Data API v3 in your Google Cloud project. Create an OAuth client of type TVs and Limited Input devices, then enter its client ID and secret here. These are app credentials, not your Google password.",style=MaterialTheme.typography.bodyLarge) }
        item { Text(if(state.browserConnected) "Music home connected · ${state.browserProfile.orEmpty()}" else "Music home and mixes need a one-time browser connection from your computer.",style=MaterialTheme.typography.titleMedium) }
        if(state.browserConnected) item { Button(onClick=model::disconnectBrowser,enabled=!busy) { Text("Disconnect Music home") } }
        item { MusicField("OAuth TV client ID",clientId,{clientId=it}) }
        item { MusicField("OAuth client secret",secret,{secret=it},password=true) }
        item { Button(onClick={ model.saveClient(clientId,secret);secret="" },enabled=!busy && clientId.isNotBlank() && secret.isNotBlank()) { Text("Save OAuth client") } }
        if(state.clientConfigured) {
            item { Button(onClick=model::connect,enabled=!busy && state.pairing==null) { Text(if(state.authorized) "Connect another account" else "Connect account") } }
            item { Text("On Google's authorization screen, choose the Blanktarget Records channel. The channel returned by Google will be displayed below; a profile name is never assumed.",style=MaterialTheme.typography.bodyLarge) }
        }
        state.pairing?.let { pairing ->
            item { Text(pairing.verificationUrl,style=MaterialTheme.typography.titleLarge) }
            item { Text(pairing.userCode,style=MaterialTheme.typography.headlineLarge) }
            item { Text("Enter this code on your phone or computer and approve read-only YouTube access.",style=MaterialTheme.typography.bodyLarge) }
        }
        state.channel?.let { channel -> item { Text("Connected channel: ${channel.title}",style=MaterialTheme.typography.titleLarge) } }
        item { Text(if(busy) "Working…" else message ?: state.message ?: if(state.clientConfigured) "OAuth client configured." else "OAuth TV client required before sign-in.",style=MaterialTheme.typography.bodyLarge) }
        if(state.clientConfigured) item { Button(onClick=model::disconnect,enabled=!busy) { Text("Disconnect and remove saved credentials") } }
        item { Text("Credentials are encrypted on this TV. Playback uses the official visible YouTube player; private, restricted or embedding-disabled videos may not play. This does not expose the full YouTube Music catalog.",style=MaterialTheme.typography.bodyMedium) }
    }
    }
}

@Composable
private fun MusicField(label:String,value:String,onChange:(String)->Unit,password:Boolean=false) {
    Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text(label,style=MaterialTheme.typography.titleMedium)
        BasicTextField(value=value,onValueChange=onChange,singleLine=true,
            keyboardOptions=KeyboardOptions(keyboardType=if(password) KeyboardType.Password else KeyboardType.Ascii,autoCorrectEnabled=false),
            visualTransformation=if(password) PasswordVisualTransformation() else VisualTransformation.None,
            cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),textStyle=MaterialTheme.typography.bodyLarge.copy(color=MaterialTheme.colorScheme.onSurface),
            modifier=Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface,RoundedCornerShape(10.dp))
                .border(1.dp,MaterialTheme.colorScheme.primary,RoundedCornerShape(10.dp)).padding(15.dp))
    }
}
