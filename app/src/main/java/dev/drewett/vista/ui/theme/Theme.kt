package dev.drewett.vista.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.drewett.vista.data.system.AppearanceSettings
import dev.drewett.vista.data.system.AppearanceColors
import dev.drewett.vista.data.system.SettingsRepository

val LocalVistaAppearance=staticCompositionLocalOf { AppearanceSettings() }
fun appearanceColor(value:String)=Color(AppearanceColors.argb(value).toInt())

/**
 * Adaptive day/night theme. Day = light & bright (projector readability in daylight);
 * night = deep cinematic. Driven by the system uiMode we control elsewhere.
 */
/**
 * The system's night state, used by the Auto theme. Fire OS has no day/night setting and always
 * reports day, so Auto stays dark there (the Fire TV's own UI is dark too).
 */
@Composable
fun vistaSystemDark(): Boolean =
    isSystemInDarkTheme() || dev.drewett.vista.data.system.Platform.isFireOs(LocalContext.current)

@Composable
fun VistaTheme(
    dark: Boolean = vistaSystemDark(),
    content: @Composable () -> Unit,
) {
    val context=LocalContext.current
    val settings=remember(context) { SettingsRepository(context.applicationContext) }
    val appearance by settings.appearance.collectAsStateWithLifecycle(initialValue=AppearanceSettings())
    val accent=appearanceColor(appearance.focus)
    val base=if(dark) VistaDarkColors else VistaLightColors
    val background=if(appearance.customGradient) lerp(appearanceColor(appearance.gradientStart),appearanceColor(appearance.gradientEnd),0.5f) else base.background
    CompositionLocalProvider(LocalVistaAppearance provides appearance) {
    MaterialTheme(
        colorScheme = base.copy(primary=accent,onPrimary=if(accent.luminance()>0.45f) Color(0xFF0A1729) else Color.White,
            background=background,onBackground=if(appearance.customGradient) { if(background.luminance()>0.45f) Color(0xFF101827) else Color(0xFFF2F6FF) } else base.onBackground),
        typography = VistaTypography,
        content = { CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground, content = content) },
    )
    }
}
