package dev.drewett.vista

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.drewett.vista.data.tv.LgTvRemote
import dev.drewett.vista.data.tv.RemoteInputButtons
import dev.drewett.vista.domain.ThemeMode
import dev.drewett.vista.ui.home.SettingsViewModel
import dev.drewett.vista.ui.home.isHomeRedirectEnabled
import dev.drewett.vista.ui.theme.VistaTheme
import kotlinx.coroutines.launch
import dev.drewett.vista.ui.components.VistaButton as Button

/** Remote buttons that switch the LG TV to HDMI 1-4, and pairing with the TV. */
class TvInputsSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings: SettingsViewModel = viewModel()
            val theme by settings.themeMode.collectAsStateWithLifecycle()
            val dark = when (theme) { ThemeMode.AUTO -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
            VistaTheme(dark = dark) { TvInputsScreen(onDone = { finish() }) }
        }
    }
}

@Composable
private fun TvInputsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val tv = remember { LgTvRemote(context) }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(tv.pairing.describe()) }
    var busy by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(RemoteInputButtons.enabled(context)) }
    var listeningFor by remember { mutableStateOf<Int?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    // While waiting for a press, the key service hands the next button to us instead of switching.
    DisposableEffect(listeningFor) {
        val port = listeningFor
        RemoteInputButtons.capture = if (port == null) null else { key ->
            RemoteInputButtons.assign(context, port, key)
            listeningFor = null; revision++
        }
        onDispose { RemoteInputButtons.capture = null }
    }
    BackHandler { if (listeningFor != null) listeningFor = null else onDone() }

    fun run(action: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch { status = runCatching { action() }.getOrElse { it.message ?: "Something went wrong." }; busy = false }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 56.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("TV inputs", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text("The four app buttons at the bottom of the remote switch your LG TV to HDMI 1–4. Vista asks the TV over your home network; the first time, the TV asks you to allow it.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f), modifier = Modifier.widthIn(max = 980.dp))
        if (!isHomeRedirectEnabled(context)) Text("Vista's accessibility service is off, so remote buttons can't be remapped. Turn on Vista in Settings › System › Accessibility.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        Text(if (busy) "Working… (if the TV shows a prompt, choose Allow)" else status,
            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { run { when (val r = tv.pair(fresh = tv.pairing?.clientKey != null)) { is LgTvRemote.Outcome.Failed -> r.message; else -> tv.pairing.describe() } } },
                enabled = !busy, modifier = Modifier.focusRequester(firstFocus)) { Text(if (tv.pairing?.clientKey == null) "Find & pair TV" else "Pair again") }
            Button(onClick = { enabled = !enabled; RemoteInputButtons.setEnabled(context, enabled) }) { Text(if (enabled) "Buttons: on" else "Buttons: off") }
            Button(onClick = { RemoteInputButtons.resetDefaults(context); revision++ }) { Text("Reset buttons") }
            if (tv.pairing != null) Button(onClick = { tv.forget(); status = tv.pairing.describe() }) { Text("Forget TV") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items((1..RemoteInputButtons.PORTS).toList(), key = { "$it:$revision" }) { port ->
                val key = RemoteInputButtons.keyFor(context, port)
                Row(Modifier.fillMaxWidth().widthIn(max = 980.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("HDMI $port", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(if (listeningFor == port) "Press the remote button for HDMI $port… (Back to cancel)" else RemoteInputButtons.name(key),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                    }
                    Button(onClick = { run { when (val r = tv.switchToHdmi(port)) { is LgTvRemote.Outcome.Failed -> r.message; else -> "Switched to HDMI $port." } } },
                        enabled = !busy) { Text("Switch now") }
                    Button(onClick = { listeningFor = port }) { Text("Change button") }
                }
            }
        }
    }
}

private fun dev.drewett.vista.data.tv.LgTvPairing?.describe(): String = when {
    this == null -> "No TV paired yet."
    clientKey == null -> "Found the TV at $host, not paired yet."
    permissions != LgTvRemote.PERMISSIONS_VERSION -> "The TV at $host needs to allow Vista once more. Choose Pair again, then Allow on the TV."
    else -> "Paired with the LG TV at $host."
}
