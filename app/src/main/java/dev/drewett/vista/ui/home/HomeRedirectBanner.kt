package dev.drewett.vista.ui.home

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.drewett.vista.data.system.HomeRedirect
import dev.drewett.vista.data.system.Platform
import dev.drewett.vista.ui.components.VistaButton as Button

/** True when Vista's Home-button redirect (its accessibility service) is switched on. */
fun isHomeRedirectEnabled(context: Context): Boolean = HomeRedirect.isEnabled(context)

/**
 * Without the redirect, Home falls back to the stock TV screen. Android can switch the service off
 * on its own, so say so plainly instead of failing silently. Re-checked whenever Vista resumes.
 * On Fire OS (no real Accessibility page) Vista turns it back on itself when it holds the ADB grant;
 * the banner only shows if that is impossible, with the one command that fixes it.
 */
@Composable
fun HomeRedirectBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var enabled by remember { mutableStateOf(HomeRedirect.ensureEnabled(context)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) enabled = HomeRedirect.ensureEnabled(context) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (enabled) return
    val fire = Platform.isFireOs(context)
    Row(modifier.widthIn(max = 760.dp).background(Color(0xF22A1414), RoundedCornerShape(16.dp)).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("The Home button is going to ${Platform.systemName(context)}", color = Color.White, style = MaterialTheme.typography.titleSmall)
            Text(
                if (fire) "Vista needs a one-time permission to keep Home here. From the computer run: ${HomeRedirect.grantCommand(context)}"
                else "Turn on Vista in Settings › System › Accessibility so Home always opens here.",
                color = Color(0xFFE8C9C9), style = MaterialTheme.typography.bodySmall)
        }
        if (fire) Button(onClick = { enabled = HomeRedirect.ensureEnabled(context) }) { Text("Try again") }
        else Button(onClick = {
            runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }) { Text("Open settings") }
    }
}
