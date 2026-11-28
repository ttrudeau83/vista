package dev.drewett.vista.data.system

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import dev.drewett.vista.service.VistaAccessibilityService

/**
 * State of Vista's Home-button redirect (its accessibility service).
 *
 * Google TV: the user turns it on in Settings › Accessibility; Vista never changes it.
 * Fire OS: the Accessibility page is a dummy, so (user-approved 2026-11-27) Vista re-enables
 * its own service when it finds it off, using an ADB-granted WRITE_SECURE_SETTINGS.
 * Only Vista's own entry is ever added; other enabled services are kept as they are.
 */
object HomeRedirect {
    private const val TAG = "VistaHomeRedirect"

    private fun ours(context: Context) = ComponentName(context, VistaAccessibilityService::class.java)

    fun isEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours(context) }
    }

    fun canSelfHeal(context: Context): Boolean =
        Platform.isFireOs(context) &&
            context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** The one-time command that lets Vista keep its redirect on (shown when the grant is missing). */
    fun grantCommand(context: Context) =
        "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

    /** Turns the redirect back on if it is off. Returns true when it is on afterwards. */
    fun ensureEnabled(context: Context): Boolean {
        if (isEnabled(context)) return true
        if (!canSelfHeal(context)) return false
        return runCatching {
            val resolver = context.contentResolver
            val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                .orEmpty().split(':').filter { it.isNotBlank() }
            val updated = (current + ours(context).flattenToString()).distinct().joinToString(":")
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated)
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            Log.i(TAG, "Home redirect re-enabled")
            isEnabled(context)
        }.getOrElse { Log.w(TAG, "Could not re-enable Home redirect: ${it.javaClass.simpleName}"); false }
    }
}
