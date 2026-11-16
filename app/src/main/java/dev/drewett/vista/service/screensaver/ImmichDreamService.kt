package dev.drewett.vista.service.screensaver

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.service.dreams.DreamService
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import dev.drewett.vista.data.immich.ImmichRepository
import dev.drewett.vista.data.system.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** OS-managed idle screensaver. Never starts itself or takes audio focus. */
class ImmichDreamService : DreamService() {
    private var running: CoroutineScope? = null
    private lateinit var root: FrameLayout
    private lateinit var image: ImageView
    private lateinit var clock: TextView
    private var use24Hour = false
    private var clockPosition = 0
    private var currentBitmap: Bitmap? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Fail closed if media state cannot be determined. Do not show photos over active audio.
        if (mediaPlaying()) { finish(); return }
        isInteractive = false // Android dismisses the dream on the first real remote input.
        isFullscreen = true
        isScreenBright = false
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        root.addView(image, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x18000000, Color.TRANSPARENT, 0x40000000))
        }, FrameLayout.LayoutParams(-1, -1))
        clock = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 38f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setShadowLayer(5f, 0f, 2f, Color.BLACK)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = GradientDrawable().apply { setColor(0x50000000); cornerRadius = dp(14).toFloat() }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        root.addView(clock, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START))
        setContentView(root)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        if (!::root.isInitialized || mediaPlaying()) { finish(); return }
        running?.cancel()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { running = it }
        val repository = ImmichRepository.get(this)
        val settings = SettingsRepository(this)
        scope.launch {
            // No controls, playback commands or audio-focus changes are sent to other apps.
            while (isActive) {
                if (mediaPlaying()) { finish(); return@launch }
                delay(1_000)
            }
        }
        scope.launch { settings.use24Hour.collectLatest { use24Hour = it; updateClock() } }
        scope.launch { settings.screensaverClock.collectLatest { clock.visibility = if (it) View.VISIBLE else View.GONE } }
        scope.launch {
            var seconds = 0
            while (isActive) {
                updateClock()
                if (seconds++ % 30 == 0) moveClock()
                delay(1_000)
            }
        }
        scope.launch {
            repository.config.collectLatest { config ->
                if (config.loading) return@collectLatest
                // An unconfigured dream cannot show private photos from a prior account.
                image.setImageDrawable(null)
                currentBitmap = null
                if (!config.configured || config.albumId == null) { finish(); return@collectLatest }
                image.scaleType = if (config.fit) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
                var previousFile: File? = null
                var first = true
                while (isActive) {
                    if (mediaPlaying()) { finish(); return@collectLatest }
                    val file = try {
                        if (first) repository.photo() else repository.nextPhoto()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { repository.cachedPhoto(previousFile) }
                    first = false
                    if (file != null) {
                        val bitmap = decode(file)
                        currentCoroutineContext().ensureActive()
                        if (mediaPlaying()) { finish(); return@collectLatest }
                        if (bitmap != null) {
                            image.setImageBitmap(bitmap)
                            currentBitmap = bitmap
                            previousFile = file
                        } else if (currentBitmap == null) {
                            finish()
                            return@collectLatest
                        }
                    } else if (currentBitmap == null) {
                        // No successful image and no offline cache: leave the system in control.
                        finish()
                        return@collectLatest
                    }
                    delay(30_000)
                }
            }
        }
    }

    override fun onDreamingStopped() {
        stopWork()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        stopWork()
        super.onDetachedFromWindow()
    }

    override fun onDestroy() {
        stopWork()
        super.onDestroy()
    }

    private fun stopWork() {
        running?.cancel()
        running = null
        if (::clock.isInitialized) clock.animate().cancel()
        if (::image.isInitialized) image.setImageDrawable(null)
        currentBitmap = null
    }

    private fun mediaPlaying(): Boolean = try {
        (getSystemService(AUDIO_SERVICE) as? AudioManager)?.isMusicActive ?: true
    } catch (_: Exception) { true }

    private suspend fun decode(file: File): Bitmap? = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        var sample = 1
        while (bounds.outWidth / sample > 1920 || bounds.outHeight / sample > 1080) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun updateClock() {
        clock.text = SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm a", Locale.getDefault()).format(Date())
    }

    private fun moveClock() {
        root.post {
            if (running == null || root.width == 0 || root.height == 0) return@post
            val margin = dp(40)
            val maxX = (root.width - clock.width - margin).coerceAtLeast(margin)
            val maxY = (root.height - clock.height - margin).coerceAtLeast(margin)
            val position = clockPosition++ % 4
            // Four changing corners plus a small offset avoid fixing clock pixels in one place.
            val offset = dp((clockPosition * 7) % 28)
            val x = if (position == 0 || position == 3) margin + offset else maxX - offset
            val y = if (position < 2) margin + offset else maxY - offset
            clock.animate().x(x.toFloat()).y(y.toFloat()).setDuration(700).start()
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
