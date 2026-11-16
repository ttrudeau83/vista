package dev.drewett.vista.data.system

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.drewett.vista.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Persists Vista's appearance preferences independently from Google TV settings. */
class SettingsRepository(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_mode")
    private val use24HourKey = booleanPreferencesKey("use_24_hour_clock")
    private val screensaverClockKey = booleanPreferencesKey("screensaver_clock")

    val use24Hour: Flow<Boolean> = context.settingsDataStore.data.map { it[use24HourKey] ?: false }
    val screensaverClock: Flow<Boolean> = context.settingsDataStore.data.map { it[screensaverClockKey] ?: true }

    suspend fun setUse24Hour(enabled: Boolean) {
        context.settingsDataStore.edit { it[use24HourKey] = enabled }
    }

    suspend fun setScreensaverClock(enabled: Boolean) {
        context.settingsDataStore.edit { it[screensaverClockKey] = enabled }
    }

    val themeMode: Flow<ThemeMode> =
        context.settingsDataStore.data.map { prefs ->
            runCatching { ThemeMode.valueOf(prefs[themeKey] ?: "") }.getOrDefault(ThemeMode.AUTO)
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[themeKey] = mode.name }
    }
}
