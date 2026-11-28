package dev.drewett.vista.data.channels

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dev.drewett.vista.data.system.Platform
import kotlin.concurrent.thread

/**
 * Android TV's home screen sends INITIALIZE_PROGRAMS so apps create their home-screen channels
 * (Nuvio's Continue Watching, etc.). Fire OS's launcher never does, so on Fire OS Vista sends it
 * itself, explicitly to each app that listens. Apps treat it as "make sure your channels exist",
 * so repeating it is harmless. Google TV already does this, so Vista leaves it alone there.
 */
object ChannelInitializer {
    private const val ACTION = "android.media.tv.action.INITIALIZE_PROGRAMS"
    @Volatile private var sentThisProcess = false

    fun requestOnFireOs(context: Context) {
        if (sentThisProcess || !Platform.isFireOs(context)) return
        sentThisProcess = true
        val app = context.applicationContext
        thread(name = "vista-channel-init", isDaemon = true) {
            runCatching {
                app.packageManager.queryBroadcastReceivers(Intent(ACTION), 0).forEach { info ->
                    val receiver = info.activityInfo ?: return@forEach
                    runCatching { app.sendBroadcast(Intent(ACTION).setComponent(ComponentName(receiver.packageName, receiver.name))) }
                }
            }
        }
    }
}
