package dev.drewett.vista.ui.photos

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.widget.*
import dev.drewett.vista.data.immich.ImmichRepository
import dev.drewett.vista.data.system.SettingsRepository
import dev.drewett.vista.data.weather.WeatherRepository
import dev.drewett.vista.service.VistaNotificationListenerService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Shared ambient renderer: no audio focus or playback commands, and no UI screenshots. */
class AmbientPhotoView(context: Context, private val allowMusic: Boolean, private val exit: () -> Unit) : FrameLayout(context) {
    private var work: CoroutineScope? = null
    private val repository = ImmichRepository.get(context)
    private val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val corner = TextView(context).apply { setTextColor(Color.WHITE); textSize = 25f; setShadowLayer(5f,0f,2f,Color.BLACK); setPadding(18,12,18,12); setBackgroundColor(0x38000000) }
    private val song = TextView(context).apply { setTextColor(Color.WHITE); textSize = 17f; setShadowLayer(4f,0f,2f,Color.BLACK) }
    private var use24Hour = false
    private var showClock = true
    private var weather = ""
    private var photoCaption = ""
    private var currentFile: File? = null
    private var popup: LinearLayout? = null
    private val handler = Handler(Looper.getMainLooper())
    private var suppressRelease = false
    private val longSelect = Runnable { suppressRelease = true; showPhotoMenu() }
    init {
        setBackgroundColor(Color.BLACK)
        addView(image, LayoutParams(-1,-1))
        addView(corner, LayoutParams(-2,-2,Gravity.TOP or Gravity.END).apply { setMargins(40,40,40,40) })
        addView(song, LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.START).apply { setMargins(48,36,48,36) })
    }
    fun start() {
        if (work != null) return
        keepScreenOn = true
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { work = it }
        val settings = SettingsRepository(context)
        scope.launch { settings.use24Hour.collectLatest { use24Hour = it; updateCorner() } }
        scope.launch { settings.screensaverClock.collectLatest { showClock = it; updateCorner() } }
        scope.launch {
            repository.config.collectLatest {
                image.scaleType = if (it.fit) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
                if (!it.loading && (!it.configured || it.albumId == null)) exit()
            }
        }
        scope.launch {
            combine(repository.config,repository.currentPhoto) { config,asset ->
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
                if (!allowMusic && musicActive()) { exit(); return@launch }
                updateCorner(); song.text = listOf(photoCaption,nowPlaying()).filter(String::isNotBlank).joinToString("\n"); delay(1_000)
            }
        }
        scope.launch {
            val first = try { repository.photo() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { repository.cachedPhoto(currentFile) }
            if (first != null) display(first) else { exit(); return@launch }
            launch { try { repository.loadGallery() } catch (e: CancellationException) { throw e } catch (_: Exception) { } }
            while (isActive) {
                delay(20_000)
                if (popup == null) try { advance() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* Retry next tick; a failed slide must not end rotation. */ }
            }
        }
    }
    fun stop() {
        keepScreenOn = false
        handler.removeCallbacksAndMessages(null); work?.cancel(); work = null
        image.animate().cancel(); image.setImageDrawable(null); currentFile = null
        popup?.let(::removeView); popup = null
    }
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
        if (popup != null || currentFile == null) return
        image.animate().cancel()
        val menu = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(28,24,28,24); setBackgroundColor(0xEF152030.toInt()) }
        menu.addView(TextView(context).apply { text = "Hide this photo only in Vista?"; setTextColor(Color.WHITE); textSize = 22f })
        val hide = Button(context).apply {
            text = "Hide photo and continue"
            setOnClickListener { work?.launch { try {
                repository.hideCurrent(); dismissMenu()
                val next = repository.nextPhoto()
                if (next != null) display(next) else exit()
            } catch (e: CancellationException) { throw e } catch (_: Exception) { dismissMenu(); exit() } } }
        }
        menu.addView(hide)
        menu.addView(Button(context).apply { text = "Keep photo and continue"; setOnClickListener { dismissMenu() } })
        addView(menu,LayoutParams(-2,-2,Gravity.CENTER)); popup = menu; hide.requestFocus()
    }
    private fun dismissMenu() { popup?.let(::removeView); popup = null; animatePhoto() }
    private suspend fun advance() {
        val next = try { repository.nextPhoto() } catch (e: CancellationException) { throw e } catch (_: Exception) { repository.cachedPhoto(currentFile) }
        if (next != null) display(next)
    }
    private suspend fun display(file: File) {
        if (currentFile == file) return
        val bitmap = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath,bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            while ((bounds.outWidth.toLong()/sample)*(bounds.outHeight/sample) > 16_000_000 ||
                (bounds.outWidth/(sample*2) >= 3840 && bounds.outHeight/(sample*2) >= 2160)) sample *= 2
            BitmapFactory.decodeFile(file.absolutePath,BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })
        } ?: return
        image.animate().cancel(); image.setImageBitmap(bitmap); currentFile = file; animatePhoto()
    }
    private fun animatePhoto() {
        image.animate().cancel(); image.scaleX = 1f; image.scaleY = 1f; image.translationX = 0f
        image.animate().scaleX(1.045f).scaleY(1.045f).translationX(8f).setDuration(20_000).setInterpolator(android.view.animation.LinearInterpolator()).start()
    }
    private fun updateCorner() {
        val time = if (showClock) SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm a",Locale.getDefault()).format(Date()) else ""
        corner.text = listOf(time,weather).filter(String::isNotBlank).joinToString("\n")
        corner.visibility = if (corner.text.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
    }
    private fun musicActive(): Boolean = try { (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).isMusicActive } catch (_: Exception) { false }
    private fun nowPlaying(): String {
        if (!musicActive()) return ""
        return try {
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val session = manager.getActiveSessions(ComponentName(context,VistaNotificationListenerService::class.java)).firstOrNull { it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING }
            val metadata = session?.metadata
            listOfNotNull(metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)).joinToString(" · ").ifBlank { "Music playing" }
        } catch (_: SecurityException) { "Music playing" }
    }
}
