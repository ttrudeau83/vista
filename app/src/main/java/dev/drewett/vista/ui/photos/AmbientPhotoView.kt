package dev.drewett.vista.ui.photos

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.*
import dev.drewett.vista.data.immich.AmbientMedia
import dev.drewett.vista.data.immich.ImmichRepository
import dev.drewett.vista.data.immich.MediaKind
import dev.drewett.vista.data.system.PhotoMotion
import dev.drewett.vista.data.system.SettingsRepository
import dev.drewett.vista.data.weather.WeatherRepository
import dev.drewett.vista.service.VistaNotificationListenerService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Shared local media renderer. Video is always muted and never requests audio focus. */
class AmbientPhotoView(context: Context, private val allowMusic: Boolean, private val exit: () -> Unit) : FrameLayout(context) {
    private var work: CoroutineScope? = null
    private val repository = ImmichRepository.get(context)
    private val image = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    private val video = TextureView(context).apply { visibility = View.GONE; isOpaque = false }
    private val corner = TextView(context).apply { setTextColor(Color.WHITE); textSize = 25f; setShadowLayer(5f,0f,2f,Color.BLACK); setPadding(18,12,18,12); setBackgroundColor(0x38000000) }
    private val song = TextView(context).apply { setTextColor(Color.WHITE); textSize = 17f; setShadowLayer(4f,0f,2f,Color.BLACK) }
    private var use24Hour = false
    private var showClock = true
    private var fit = true
    private var motion = PhotoMotion.SUBTLE
    private var includeVideos = true
    private var weather = ""
    private var photoCaption = ""
    private var currentMedia: AmbientMedia? = null
    private var pendingVideo: AmbientMedia? = null
    private var player: MediaPlayer? = null
    private var playerSurface: Surface? = null
    private var renderReady: CompletableDeferred<Boolean>? = null
    private var videoWidth = 0
    private var videoHeight = 0
    private var ownVideoUntil = 0L
    private var videoGeneration = 0
    private var popup: LinearLayout? = null
    private val advanceSignal = Channel<Unit>(Channel.CONFLATED)
    private val handler = Handler(Looper.getMainLooper())
    private var suppressRelease = false
    private val longSelect = Runnable { suppressRelease = true; showPhotoMenu() }
    init {
        setBackgroundColor(Color.BLACK)
        addView(image, LayoutParams(-1,-1))
        addView(video, LayoutParams(-1,-1))
        addView(corner, LayoutParams(-2,-2,Gravity.TOP or Gravity.END).apply { setMargins(40,40,40,40) })
        addView(song, LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.START).apply { setMargins(48,36,48,36) })
        video.surfaceTextureListener = object: TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture,width: Int,height: Int) {
                pendingVideo?.let { prepareVideo(it,Surface(texture)) }
            }
            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture,width: Int,height: Int) { frameVideo() }
            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean { releaseVideo(); return true }
            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
        }
    }
    fun start() {
        if (work != null) return
        keepScreenOn = true
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { work = it }
        val settings = SettingsRepository(context)
        scope.launch { settings.use24Hour.collectLatest { use24Hour = it; updateCorner() } }
        scope.launch { settings.screensaverClock.collectLatest { showClock = it; updateCorner() } }
        scope.launch { settings.ambientFit.collectLatest {
            fit = it; image.scaleType = if(it) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
            frameVideo(); animatePhoto()
        } }
        scope.launch { settings.photoMotion.collectLatest { motion = it; animatePhoto() } }
        scope.launch { settings.ambientVideos.collectLatest { includeVideos = it; if(!it && currentMedia?.kind == MediaKind.VIDEO) nextMedia() } }
        scope.launch {
            repository.config.collectLatest {
                if (!it.loading && (!it.configured || it.albumId == null)) exit()
            }
        }
        scope.launch {
            combine(repository.config,repository.currentAmbient) { config,asset ->
                if (config.slideshowMode == dev.drewett.vista.data.immich.SlideshowMode.ON_THIS_DAY) {
                    val date = try { java.time.LocalDate.parse(asset?.date) } catch (_: Exception) { null }
                    date?.let { "On this day · ${java.time.LocalDate.now().year - it.year} years ago" }.orEmpty()
                } else ""
            }.collectLatest { photoCaption = it }
        }
        scope.launch {
            val source = WeatherRepository.get(context)
            launch { while (isActive) { source.refresh(); delay(15 * 60_000) } }
            source.state.collectLatest {
                weather = if (it.temperatureF != null) "${it.city} · ${it.temperatureF}°F · ${it.condition}" else ""
                updateCorner()
            }
        }
        scope.launch {
            while (isActive) {
                // A muted local video's audio track must not be mistaken for another music app.
                if (!allowMusic && player == null && SystemClock.elapsedRealtime() > ownVideoUntil && musicActive()) { exit(); return@launch }
                updateCorner(); song.text = listOf(photoCaption,nowPlaying()).filter(String::isNotBlank).joinToString("\n"); delay(1_000)
            }
        }
        scope.launch {
            launch { try { repository.loadGallery() } catch (e: CancellationException) { throw e } catch (_: Exception) { } }
            var advance = false
            while (isActive) {
                val next = try { repository.ambientMedia(advance = advance,includeVideos = includeVideos) }
                    catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                if(next != null && next.file != currentMedia?.file) {
                    val displayed = try { display(next) } catch(e: CancellationException) { throw e } catch(_: Exception) { false }
                    if(displayed) repository.markAmbientShown(next.id) else {
                        repository.skipAmbientMedia(next.id)
                        advance = true
                        delay(500)
                        continue
                    }
                }
                if(currentMedia == null) song.text = "Preparing fresh media…"
                // While the next unseen file downloads, hold the current visual instead of repeating a seen file.
                val duration = if(next == null) 2_000L else if(currentMedia?.kind == MediaKind.VIDEO) 60_000L else 20_000L
                withTimeoutOrNull(duration) { advanceSignal.receive() }
                while(popup != null && isActive) delay(150)
                advance = true
            }
        }
    }
    fun stop() {
        keepScreenOn = false
        handler.removeCallbacksAndMessages(null); work?.cancel(); work = null
        releaseVideo()
        image.animate().cancel(); image.setImageDrawable(null); currentMedia = null
        popup?.let(::removeView); popup = null
        while(advanceSignal.tryReceive().isSuccess) { }
        repository.endAmbientSession()
    }
    fun nextMedia() { advanceSignal.trySend(Unit) }
    fun showOptions() { showPhotoMenu() }
    fun handleKey(event: KeyEvent): Boolean {
        val select = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER
        if (select && suppressRelease) { if (event.action == KeyEvent.ACTION_UP) suppressRelease = false; return true }
        if (popup != null) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) { dismissMenu(); return true }
            return false
        }
        if (select) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) handler.postDelayed(longSelect,550)
            if (event.action == KeyEvent.ACTION_UP) { handler.removeCallbacks(longSelect); exit() }
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (event.action == KeyEvent.ACTION_UP) exit()
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) { exit(); return true }
        return false
    }
    private fun showPhotoMenu() {
        val media = currentMedia ?: return
        if (popup != null) return
        image.animate().cancel()
        runCatching { player?.pause() }
        val label = if(media.kind == MediaKind.VIDEO) "video" else "photo"
        val menu = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(28,24,28,24); setBackgroundColor(0xEF152030.toInt()) }
        menu.addView(TextView(context).apply { text = "Hide this $label only in Vista?"; setTextColor(Color.WHITE); textSize = 22f })
        val hide = Button(context).apply {
            text = "Hide and continue"
            setOnClickListener { work?.launch {
                try { repository.hideAsset(media.id) }
                catch(e: CancellationException) { throw e }
                catch(_: Exception) { }
                dismissMenu(); nextMedia()
            } }
        }
        menu.addView(hide)
        menu.addView(Button(context).apply { text = "Keep and continue"; setOnClickListener { dismissMenu() } })
        addView(menu,LayoutParams(-2,-2,Gravity.CENTER)); popup = menu; hide.requestFocus()
    }
    private fun dismissMenu() {
        popup?.let(::removeView); popup = null
        if(currentMedia?.kind == MediaKind.VIDEO) runCatching { player?.setVolume(0f,0f);player?.start() } else animatePhoto()
    }
    private suspend fun display(media: AmbientMedia): Boolean {
        if(media.kind == MediaKind.VIDEO) return displayVideo(media)
        val bitmap = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(media.file.absolutePath,bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            while ((bounds.outWidth.toLong()/sample)*(bounds.outHeight/sample) > 16_000_000 ||
                (bounds.outWidth/(sample*2) >= 3840 && bounds.outHeight/(sample*2) >= 2160)) sample *= 2
            BitmapFactory.decodeFile(media.file.absolutePath,BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })
        } ?: return false
        releaseVideo()
        image.visibility = View.VISIBLE
        image.animate().cancel(); image.setImageBitmap(bitmap); currentMedia = media; animatePhoto()
        return true
    }
    private suspend fun displayVideo(media: AmbientMedia): Boolean {
        releaseVideo(hide = false)
        while(advanceSignal.tryReceive().isSuccess) { }
        val ready = CompletableDeferred<Boolean>().also { renderReady = it }
        pendingVideo = media
        video.alpha = 0f; video.visibility = View.VISIBLE
        if(video.isAvailable) video.surfaceTexture?.let { prepareVideo(media,Surface(it)) }
        val displayed = withTimeoutOrNull(15_000) { ready.await() } == true
        if(displayed) { currentMedia = media; image.animate().cancel();video.animate().alpha(1f).setDuration(350).start() }
        else releaseVideo()
        return displayed
    }
    private fun prepareVideo(media: AmbientMedia,surface: Surface) {
        if(player != null || pendingVideo != media || work == null) { surface.release();return }
        val generation = ++videoGeneration
        val next = MediaPlayer().also { player = it;playerSurface = surface }
        try {
            next.setVolume(0f,0f)
            next.setSurface(surface)
            next.isLooping = false
            next.setOnVideoSizeChangedListener { _,width,height -> videoWidth = width;videoHeight = height;frameVideo() }
            next.setOnPreparedListener {
                if(generation != videoGeneration) return@setOnPreparedListener
                it.setVolume(0f,0f)
                frameVideo()
                if(popup == null) it.start()
            }
            next.setOnInfoListener { _,what,_ ->
                if(generation == videoGeneration && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) renderReady?.complete(true)
                false
            }
            next.setOnCompletionListener { if(generation == videoGeneration) nextMedia() }
            next.setOnErrorListener { _,_,_ ->
                if(generation == videoGeneration) {
                    renderReady?.complete(false)
                    work?.launch { repository.skipAmbientMedia(media.id) }
                    nextMedia()
                }
                true
            }
            next.setDataSource(media.file.absolutePath)
            next.prepareAsync()
        } catch(_: Exception) { renderReady?.complete(false);releaseVideo() }
    }
    private fun releaseVideo(hide: Boolean = true) {
        videoGeneration += 1
        val old = player;player = null
        pendingVideo = null
        renderReady?.complete(false);renderReady = null
        if(old != null) ownVideoUntil = SystemClock.elapsedRealtime()+2_000
        runCatching { old?.setOnPreparedListener(null);old?.setOnCompletionListener(null);old?.setOnErrorListener(null);old?.setOnInfoListener(null);old?.release() }
        playerSurface?.release();playerSurface = null
        video.animate().cancel();if(hide) video.visibility = View.GONE
        videoWidth = 0;videoHeight = 0
    }
    private fun frameVideo() {
        if(video.width<=0 || video.height<=0) return
        val (displayWidth,displayHeight) = PhotoMotionPolicy.videoSize(video.width,video.height,videoWidth,videoHeight,fit)
        video.setTransform(Matrix().apply { setScale(displayWidth/video.width,displayHeight/video.height,video.width/2f,video.height/2f) })
    }
    private var motionIndex = kotlin.random.Random.nextInt(5)
    private fun animatePhoto() {
        if(currentMedia?.kind == MediaKind.VIDEO) return
        image.animate().cancel()
        val plan = PhotoMotionPolicy.plan(motion,motionIndex++)
        image.scaleX = plan.start.scale;image.scaleY = plan.start.scale
        image.translationX = image.width*plan.start.x;image.translationY = image.height*plan.start.y
        image.animate().scaleX(plan.end.scale).scaleY(plan.end.scale)
            .translationX(image.width*plan.end.x).translationY(image.height*plan.end.y)
            .setDuration(20_000).setInterpolator(android.view.animation.LinearInterpolator()).start()
    }
    private fun updateCorner() {
        val time = if (showClock) SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm a",Locale.getDefault()).format(Date()) else ""
        corner.text = listOf(time,weather).filter(String::isNotBlank).joinToString("\n")
        corner.visibility = if (corner.text.isBlank()) View.GONE else View.VISIBLE
    }
    private fun musicActive(): Boolean = try { (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).isMusicActive } catch (_: Exception) { false }
    private fun nowPlaying(): String {
        if (!musicActive()) return ""
        val fallback = if(player == null) "Music playing" else ""
        return try {
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val session = manager.getActiveSessions(ComponentName(context,VistaNotificationListenerService::class.java)).firstOrNull { it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING }
            val metadata = session?.metadata
            listOfNotNull(metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)).joinToString(" · ").ifBlank { fallback }
        } catch (_: SecurityException) { fallback }
    }
}
