package dev.drewett.vista

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.drewett.vista.domain.ThemeMode
import dev.drewett.vista.ui.home.SettingsViewModel
import dev.drewett.vista.ui.settings.ImmichSettingsScreen
import dev.drewett.vista.ui.theme.VistaTheme

/** Local-only configuration screen; no credential intent extras or exported endpoints. */
class ImmichSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            val settings: SettingsViewModel = viewModel()
            val mode by settings.themeMode.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.AUTO -> dev.drewett.vista.ui.theme.vistaSystemDark()
            }
            VistaTheme(dark = dark) { ImmichSettingsScreen(onDone = { finish() }) }
        }
    }
}
