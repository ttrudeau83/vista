package dev.drewett.vista.ui.music

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.drewett.vista.data.music.MusicProtocol

/** OS WebView + official IFrame player; no stream extraction, hidden playback or ad filtering. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OfficialYouTubePlayer(videoId:String,modifier:Modifier=Modifier) {
    require(MusicProtocol.videoId(videoId))
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val web=remember {
        runCatching { WebView(context).apply {
            settings.javaScriptEnabled=true
            settings.domStorageEnabled=true
            // The native track selection is the user's play gesture; no player exists before it.
            settings.mediaPlaybackRequiresUserGesture=false
            settings.allowFileAccess=false
            settings.allowContentAccess=false
            settings.mixedContentMode=android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient=object:WebViewClient() {
                override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                    // Keep player interactions in this screen; never route them to YouTube TV.
                    return request.url.scheme != "https"
                }
            }
        } }.getOrNull()
    }
    if(web==null) {
        Text("A compatible Android WebView is required for the embedded player. Update Android System WebView on this device.",modifier=modifier,color=MaterialTheme.colorScheme.onBackground)
        return
    }
    DisposableEffect(web,lifecycle) {
        val observer=LifecycleEventObserver { _,event ->
            if(event==Lifecycle.Event.ON_STOP) { web.evaluateJavascript("if(window.player){player.pauseVideo();}",null);web.onPause() }
            if(event==Lifecycle.Event.ON_RESUME) web.onResume()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer);web.stopLoading();web.loadUrl("about:blank");web.destroy() }
    }
    AndroidView(factory={ web },modifier=modifier,update={ view ->
        if(view.tag!=videoId) {
            view.tag=videoId
            val origin="https://${context.packageName}"
            val html="""<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head><body style="margin:0;background:black"><div id="player"></div><script>var player;function onYouTubeIframeAPIReady(){player=new YT.Player('player',{width:'100%',height:window.innerHeight,videoId:'$videoId',playerVars:{origin:'$origin',autoplay:1,playsinline:1,controls:1}});}</script><script src="https://www.youtube.com/iframe_api"></script></body></html>"""
            // Official API Client Identity requirement for local HTML WebView embeds.
            view.loadDataWithBaseURL(origin,html,"text/html","UTF-8",null)
        }
    })
}
