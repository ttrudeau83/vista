package dev.drewett.vista.data.system

import android.content.ComponentName
import android.content.Context
import android.os.Build

/**
 * One APK serves both TVs. Everything that differs between Google TV (onn) and Fire OS
 * (Fire TV Stick 4K Max) is decided here at runtime, so the rest of the app stays shared.
 */
object Platform {
    @Volatile private var cachedFire: Boolean? = null

    /** Fire OS declares `amazon.hardware.fire_tv`; the manufacturer check covers builds that don't. */
    fun isFireOs(context: Context): Boolean = cachedFire ?: (
        context.packageManager.hasSystemFeature("amazon.hardware.fire_tv") ||
            Build.MANUFACTURER.equals("Amazon", ignoreCase = true)
        ).also { cachedFire = it }

    /** Name of the stock TV system, for user-facing text. */
    fun systemName(context: Context) = if (isFireOs(context)) "Fire TV" else "Google TV"

    /**
     * Stock home screens the Home redirect replaces. On Fire OS the Home key goes through
     * firehomestarter (the HOME role holder) to Amazon's launcher; neither can be intercepted,
     * so the accessibility service reacts when either comes to the front.
     */
    fun stockHomeComponents(context: Context): Set<ComponentName> = if (isFireOs(context)) setOf(
        ComponentName("com.amazon.tv.launcher", "com.amazon.tv.launcher.ui.HomeActivity_vNext"),
        ComponentName("com.amazon.firehomestarter", "com.amazon.firehomestarter.HomeStarterActivity"),
    ) else emptySet()

    /** YouTube's TV app has a different package on each system. */
    val youTubePackages = listOf("com.google.android.youtube.tv", "com.amazon.firetv.youtube")

    /** Compose's Modifier.blur only renders on Android 12+; Fire OS 8 is Android 11. */
    val supportsBlur: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}
