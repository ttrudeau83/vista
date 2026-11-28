package dev.drewett.vista.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.drewett.vista.data.system.HomeRedirect

/**
 * Fire OS self-heal at boot and after Vista is updated, so the Home button keeps reaching Vista
 * even if the system cleared the accessibility setting (e.g. after an OTA). Does nothing on
 * Google TV or without the ADB grant.
 */
class HomeRedirectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> HomeRedirect.ensureEnabled(context)
        }
    }
}
