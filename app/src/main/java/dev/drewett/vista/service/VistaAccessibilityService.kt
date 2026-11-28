package dev.drewett.vista.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import dev.drewett.vista.QuickSettingsActivity
import dev.drewett.vista.MainActivity
import android.os.SystemClock
import android.widget.Toast
import dev.drewett.vista.data.tv.LgTvRemote
import dev.drewett.vista.data.tv.RemoteInputButtons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tv by lazy { LgTvRemote(this) }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val home = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo
        firmwareHome = home?.takeIf { it.packageName != packageName }?.let { android.content.ComponentName(it.packageName, it.name) }
        stockHomes = setOfNotNull(firmwareHome) + dev.drewett.vista.data.system.Platform.stockHomeComponents(this)
    }
    private var stockHomes: Set<android.content.ComponentName> = emptySet()

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
            stockHomes.any { it.packageName == event.packageName?.toString() && it.className == event.className?.toString() }) {
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
        RemoteInputButtons.capture?.let { capture ->
            if (RemoteInputButtons.assignable(event.keyCode)) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) capture(event.keyCode)
                return true
            }
        }
        RemoteInputButtons.portFor(this, event.keyCode)?.let { port ->
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) switchInput(port)
            return true // consume both down and up so the app shortcut never also opens
        }
        return false
    }

    private fun switchInput(port: Int) {
        if (!tv.paired) toast("Pairing with the TV — choose Allow on the TV screen")
        scope.launch {
            val outcome = tv.switchToHdmi(port)
            if (outcome is LgTvRemote.Outcome.Failed) toast(outcome.message)
        }
    }

    private fun toast(message: String) { runCatching { Toast.makeText(this, message, Toast.LENGTH_LONG).show() } }

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
