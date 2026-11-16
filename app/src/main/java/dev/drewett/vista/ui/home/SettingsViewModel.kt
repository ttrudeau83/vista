package dev.drewett.vista.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.drewett.vista.data.system.SettingsRepository
import dev.drewett.vista.domain.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)

    val themeMode = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.AUTO)

    val use24Hour = settingsRepository.use24Hour
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val screensaverClock = settingsRepository.screensaverClock
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun toggleClockFormat() {
        viewModelScope.launch { settingsRepository.setUse24Hour(!use24Hour.value) }
    }

    fun toggleScreensaverClock() {
        viewModelScope.launch { settingsRepository.setScreensaverClock(!screensaverClock.value) }
    }

    fun cycleTheme() {
        val next = when (themeMode.value) {
            ThemeMode.AUTO -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.AUTO
        }
        viewModelScope.launch { settingsRepository.setThemeMode(next) }
    }
}
