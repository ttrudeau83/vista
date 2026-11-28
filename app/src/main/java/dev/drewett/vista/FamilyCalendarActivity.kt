package dev.drewett.vista

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.drewett.vista.domain.ThemeMode
import dev.drewett.vista.ui.calendar.FamilyCalendarScreen
import dev.drewett.vista.ui.home.SettingsViewModel
import dev.drewett.vista.ui.theme.VistaTheme

class FamilyCalendarActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            val settings: SettingsViewModel = viewModel()
            val theme by settings.themeMode.collectAsStateWithLifecycle()
            val use24Hour by settings.use24Hour.collectAsStateWithLifecycle()
            val dark = when (theme) { ThemeMode.AUTO -> dev.drewett.vista.ui.theme.vistaSystemDark(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
            VistaTheme(dark = dark) { FamilyCalendarScreen(onDone = { finish() }, use24Hour = use24Hour) }
        }
    }
}
