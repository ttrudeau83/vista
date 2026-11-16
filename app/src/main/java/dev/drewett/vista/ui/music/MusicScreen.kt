package dev.drewett.vista.ui.music

import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.drewett.vista.MusicSettingsActivity
import dev.drewett.vista.data.music.MusicTrack
import dev.drewett.vista.ui.photos.AmbientPhotoView

@Composable
fun MusicScreen(contentFocus:FocusRequester,onAmbientChanged:(Boolean)->Unit={},model:MusicViewModel=viewModel()) {
    val context=LocalContext.current
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    var playing by remember { mutableStateOf<MusicTrack?>(null) }
    var ambient by remember { mutableStateOf(false) }
    val ambientExitFocus=remember { FocusRequester() }
    val trackPlayer=playing?.let { track ->
        remember(track.id) { movableContentOf<Modifier> { playerModifier -> OfficialYouTubePlayer(track.id,playerModifier) } }
    }
    LaunchedEffect(ambient) { onAmbientChanged(ambient);if(ambient) runCatching { ambientExitFocus.requestFocus() } }
    DisposableEffect(Unit) { onDispose { onAmbientChanged(false) } }
    LaunchedEffect(state.authorized) {
        if(!state.authorized) { ambient=false;playing=null }
        else if(state.playlists.isEmpty()) model.refresh()
    }
    BackHandler(enabled=playing!=null) { if(ambient) ambient=false else playing=null }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
    if(ambient && playing!=null && trackPlayer!=null) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            MusicAmbientPhotos(Modifier.fillMaxSize()) { ambient=false }
            Column(Modifier.align(Alignment.BottomEnd).padding(24.dp).width(384.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(playing!!.title,color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis,
                    modifier=Modifier.fillMaxWidth().background(Color.Black.copy(alpha=0.7f)).padding(8.dp),style=MaterialTheme.typography.titleMedium)
                // The complete official player stays visible; never cover its video, controls or advertising.
                trackPlayer(Modifier.width(384.dp).height(216.dp))
                Button(onClick={ ambient=false },modifier=Modifier.focusRequester(ambientExitFocus)) { Text("Return to music") }
            }
        }
    } else {
    Column(Modifier.fillMaxSize().padding(start=40.dp,end=40.dp,top=110.dp,bottom=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Music",style=MaterialTheme.typography.headlineMedium)
                Text(state.channel?.let { "YouTube · ${it.title}" } ?: "Connect Blanktarget Records",style=MaterialTheme.typography.bodyLarge)
            }
            Button(onClick={ context.startActivity(Intent(context,MusicSettingsActivity::class.java)) },modifier=Modifier.focusRequester(contentFocus)) { Text("Music settings") }
            if(state.authorized) Button(onClick=model::refresh,enabled=!busy) { Text("Refresh library") }
        }
        if(!state.authorized) {
            Text("Your own playlist frontend is ready to connect. Add a Google OAuth TV client in Music settings, then authorize the Blanktarget Records channel.",style=MaterialTheme.typography.bodyLarge)
            Text("Uses the official YouTube library and visible player. YouTube Music-specific recommendations and audio-only playback are not available through this API.",style=MaterialTheme.typography.bodyMedium)
        }
        state.pairing?.let { pair -> Text("Authorize at ${pair.verificationUrl} using ${pair.userCode}",style=MaterialTheme.typography.titleLarge) }
        if(state.channels.size>1) Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            state.channels.forEach { channel -> Button(onClick={ model.channel(channel) },enabled=!busy) { Text(channel.title) } }
        }
        val status=message ?: state.message
        if(status!=null) Text(status,style=MaterialTheme.typography.bodyMedium)
        if(busy || state.loading) Text("Loading…",style=MaterialTheme.typography.bodyMedium)
        Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(18.dp)) {
            LazyColumn(Modifier.width(250.dp).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                items(state.playlists,key={ it.id }) { playlist ->
                    Button(onClick={ ambient=false;playing=null;model.playlist(playlist) },enabled=!busy,modifier=Modifier.fillMaxWidth()) {
                        Column {
                            Text(playlist.title,maxLines=2,overflow=TextOverflow.Ellipsis)
                            Text("${playlist.count} items",style=MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                val track=playing
                if(track!=null) {
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text(track.title,modifier=Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleMedium)
                        Button(onClick={ ambient=true }) { Text("Photos + player") }
                        Button(onClick={ playing=null }) { Text("Close player") }
                    }
                    trackPlayer?.invoke(Modifier.fillMaxWidth().weight(1f))
                } else {
                    Text(state.playlist?.title ?: "Choose a playlist",style=MaterialTheme.typography.titleLarge)
                    LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(state.tracks,key={ index,item -> "${item.id}:$index" }) { _,item ->
                            Surface(onClick={ playing=item },modifier=Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(10.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                    AsyncImage(item.thumbnail,null,modifier=Modifier.width(120.dp).height(70.dp))
                                    Text(item.title,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }
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
fun MusicSettingsScreen(onDone:()->Unit,model:MusicViewModel=viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    var clientId by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    val doneFocus=remember { FocusRequester() }
    LaunchedEffect(Unit) { doneFocus.requestFocus() }
    BackHandler { secret="";onDone() }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(30.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                Text("Music account",modifier=Modifier.weight(1f),style=MaterialTheme.typography.headlineMedium)
                Button(onClick={ secret="";onDone() },modifier=Modifier.focusRequester(doneFocus)) { Text("Done") }
            }
        }
        item { Text("Enable YouTube Data API v3 in your Google Cloud project. Create an OAuth client of type TVs and Limited Input devices, then enter its client ID and secret here. These are app credentials, not your Google password.",style=MaterialTheme.typography.bodyLarge) }
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
