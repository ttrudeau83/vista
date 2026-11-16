package dev.drewett.vista.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.drewett.vista.data.system.*
import dev.drewett.vista.ui.components.VistaButton as Button
import dev.drewett.vista.ui.components.focusOutline
import dev.drewett.vista.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun AppearanceSettingsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { SettingsRepository(context.applicationContext) }
    val appearance = LocalVistaAppearance.current
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf(AppearanceColor.FOCUS) }
    val current = when(target) {
        AppearanceColor.FOCUS -> appearance.focus
        AppearanceColor.GRADIENT_START -> appearance.gradientStart
        AppearanceColor.GRADIENT_END -> appearance.gradientEnd
    }
    var hex by remember(target, current) { mutableStateOf(current) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    fun save(value: String) { scope.launch { repository.setAppearanceColor(target, value) } }
    Column(Modifier.fillMaxSize().vistaBackdrop().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Appearance", style = MaterialTheme.typography.headlineMedium)
        Text("Choose a focus outline and two background colors. Changes save immediately.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppearanceColor.entries.forEachIndexed { index, item ->
                Button(onClick = { target = item }, modifier = if(index == 0) Modifier.focusRequester(first) else Modifier) {
                    Text((if(target == item) "✓ " else "") + when(item) {
                        AppearanceColor.FOCUS -> "Focus outline"
                        AppearanceColor.GRADIENT_START -> "Gradient start"
                        AppearanceColor.GRADIENT_END -> "Gradient end"
                    })
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(76.dp).background(
            Brush.linearGradient(listOf(appearanceColor(appearance.gradientStart), appearanceColor(appearance.gradientEnd))),
            RoundedCornerShape(18.dp))) {
            Text("Background preview", Modifier.padding(20.dp).background(Color(0xAA101827), RoundedCornerShape(6.dp)).padding(6.dp), color = Color.White)
        }
        Text("Quick colors", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("#A7C8FF", "#83E8D0", "#BB9AFF", "#FF9EC4", "#FFD27A", "#FFFFFF", "#14232F", "#092329").forEach { color ->
                Button(onClick = { save(color) }, modifier = Modifier.semantics { contentDescription = "Use color $color" }, contentPadding = PaddingValues(10.dp)) {
                    Box(Modifier.size(26.dp).background(appearanceColor(color), RoundedCornerShape(7.dp)))
                }
            }
        }
        Text("Custom color · #RRGGBB")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BasicTextField(value = hex, onValueChange = { hex = it.take(7) }, singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.width(230.dp).focusOutline().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(16.dp))
            Button(onClick = { save(hex) }, enabled = AppearanceColors.normalize(hex) != null) { Text("Apply color") }
        }
        if(AppearanceColors.normalize(hex) == null) Text("Enter six hexadecimal digits, for example #83E8D0.")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Button(onClick = { scope.launch { repository.resetAppearance() } }) { Text("Reset appearance") }
            Button(onClick = onDone) { Text("Done") }
        }
    }
}
