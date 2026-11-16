package dev.drewett.vista.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import dev.drewett.vista.QuickSettingsActivity
import dev.drewett.vista.MainActivity
import android.os.SystemClock

/** A user-requested Freeplay visit may use the stock Home activity internally. */
object GoogleTvLaunchSession {
    @Volatile var freeplayActive: Boolean = false
        private set
    fun beginFreeplay() { freeplayActive = true }
    fun finish() { freeplayActive = false }
}

/**
 * Explicitly enabled Home redirection and remote Settings handling. Does not read window contents.
 */
class VistaAccessibilityService : AccessibilityService() {
    private var lastHomeLaunch = 0L
    private var firmwareHome: android.content.ComponentName? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val home = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo
        firmwareHome = home?.takeIf { it.packageName != packageName }?.let { android.content.ComponentName(it.packageName, it.name) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && GoogleTvLaunchSession.freeplayActive) {
            val target = event.packageName?.toString()
            // A separate launched app ends the visit. Ignore System UI/IME popups, which
            // have no launcher entry and may appear above Freeplay without leaving it.
            if (target != null && target != packageName && target != "com.google.android.apps.tv.launcherx" &&
                (packageManager.getLeanbackLaunchIntentForPackage(target) != null ||
                    packageManager.getLaunchIntentForPackage(target) != null)) {
                GoogleTvLaunchSession.finish()
            }
        }
        // Firmware may reserve HOME before dispatching it to key filters. Redirect only the
        // OS-resolved Home activity, never its settings, search, or other applications.
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            !GoogleTvLaunchSession.freeplayActive &&
            event.packageName?.toString() == firmwareHome?.packageName &&
            event.className?.toString() == firmwareHome?.className) {
            openHome()
        }
    }
    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_HOME) {
            GoogleTvLaunchSession.finish()
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) openHome()
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_SETTINGS) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                runCatching {
                    startActivity(
                        Intent(this, QuickSettingsActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            return true // consume both down and up so the system dashboard doesn't also open
        }
        return false
    }

    private fun openHome() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastHomeLaunch < 500) return
        lastHomeLaunch = now
        runCatching {
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
    }
}
