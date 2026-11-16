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
import dev.drewett.vista.ui.settings.AppearanceSettingsScreen
import dev.drewett.vista.ui.theme.VistaTheme

class AppearanceSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings: SettingsViewModel = viewModel()
            val theme by settings.themeMode.collectAsStateWithLifecycle()
            VistaTheme(dark = when(theme) {
                ThemeMode.AUTO -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }) { AppearanceSettingsScreen { finish() } }
        }
    }
}
