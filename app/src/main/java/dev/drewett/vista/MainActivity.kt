package dev.drewett.vista

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.drewett.vista.domain.ThemeMode
import dev.drewett.vista.service.VistaForeground
import dev.drewett.vista.ui.VistaRoot
import dev.drewett.vista.ui.VistaSignals
import dev.drewett.vista.ui.home.SettingsViewModel
import dev.drewett.vista.ui.theme.VistaTheme

class MainActivity : ComponentActivity() {
    /** Read-only shell diagnostics; never emit IDs, URLs, credentials or media content. */
    override fun dump(prefix:String,fd:java.io.FileDescriptor?,writer:java.io.PrintWriter,args:Array<out String>?) {
        super.dump(prefix,fd,writer,args)
        val repo=dev.drewett.vista.data.immich.ImmichRepository.get(this)
        val assets=repo.gallery.value
        writer.println("${prefix}VistaDiagnostics renderedVideoStarts=${repo.ambientVideoStarts.value}")
        writer.println("${prefix}VistaDiagnostics photos=${assets.count { !it.isVideo }} videos=${assets.count { it.isVideo }} cachedVideos=${assets.count { it.isVideo && it.cachedFile?.isFile==true }} currentKind=${repo.currentAmbient.value?.kind ?: "NONE"} loading=${repo.loading.value}")
        writer.println("${prefix}VistaDiagnostics status=${repo.status.value.orEmpty()}")
        writer.println("${prefix}VistaDiagnostics cache ${repo.diagnostics()}")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Fire OS never asks apps to create their home-screen channels; Vista does (once per process).
        dev.drewett.vista.data.channels.ChannelInitializer.requestOnFireOs(this)
        if (savedInstanceState == null) {
            val tab = intent?.getStringExtra(VistaSignals.EXTRA_TAB)
            // Nuvio is home: a fresh start goes there unless the Nuvio shell asked for a Vista tab.
            if (tab != null) VistaSignals.requestedTab.value = tab else VistaSignals.goNuvioHome.value = true
        }
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
            val use24Hour by settingsViewModel.use24Hour.collectAsStateWithLifecycle()
            val screensaverClock by settingsViewModel.screensaverClock.collectAsStateWithLifecycle()
            val dark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.AUTO -> dev.drewett.vista.ui.theme.vistaSystemDark()
            }
            VistaTheme(dark = dark) {
                VistaRoot(
                    themeMode = themeMode,
                    onCycleTheme = settingsViewModel::cycleTheme,
                    use24Hour = use24Hour,
                    onToggleClockFormat = settingsViewModel::toggleClockFormat,
                    screensaverClock = screensaverClock,
                    onToggleScreensaverClock = settingsViewModel::toggleScreensaverClock,
                )
            }
        }
    }

    /** HOME re-delivers the launch intent: go to Nuvio's home (or the tab the Nuvio shell asked for). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tab = intent.getStringExtra(VistaSignals.EXTRA_TAB)
        if (tab != null) VistaSignals.requestedTab.value = tab
        else { VistaSignals.reset(); VistaSignals.goNuvioHome.value = true }
    }

    override fun onResume() {
        super.onResume()
        VistaForeground.isForeground = true
        // Fire OS only (and only with the ADB grant): keep the Home redirect switched on.
        dev.drewett.vista.data.system.HomeRedirect.ensureEnabled(this)
    }

    override fun onPause() {
        super.onPause()
        VistaForeground.isForeground = false
    }
}
