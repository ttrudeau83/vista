package dev.drewett.vista.ui.trakt

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.drewett.vista.ui.components.VistaButton as Button
import dev.drewett.vista.ui.theme.vistaBackdrop

@Composable
fun TraktSettingsScreen(onDone: () -> Unit, model: TraktSettingsViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    var clientId by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    val doneFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { doneFocus.requestFocus() }
    fun done() { secret = ""; model.cancelSignIn(); onDone() }
    BackHandler { done() }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        LazyColumn(Modifier.fillMaxSize().vistaBackdrop().padding(30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("Trakt account", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                    Button(onClick = ::done, modifier = Modifier.focusRequester(doneFocus)) { Text("Done") }
                }
            }
            item { Text("Show your Trakt watchlist or a saved list in Movies and Shows. Connect the same Trakt account you use in Nuvio.", style = MaterialTheme.typography.bodyLarge) }
            if (state.authorized) {
                item { Text("Trakt connected", style = MaterialTheme.typography.titleLarge) }
                item { Text("${state.items.size} titles loaded", style = MaterialTheme.typography.bodyLarge) }
                item { Button(onClick = model::refresh, enabled = !busy && !state.loading) { Text("Refresh lists") } }
                item { Text("Show in Movies and Shows", style = MaterialTheme.typography.titleMedium) }
                item {
                    Button(onClick = { model.selectList(null) }, enabled = !busy && !state.loading) {
                        Text(if (state.selectedListId == null) "Watchlist · selected" else "Watchlist")
                    }
                }
                state.lists.forEach { list ->
                    item(key = "trakt-list:${list.id}") {
                        Button(onClick = { model.selectList(list.id) }, enabled = !busy && !state.loading) {
                            Text(if (state.selectedListId == list.id) "${list.name} · selected" else list.name)
                        }
                    }
                }
            }
            if (!state.clientConfigured) {
                item { Text("Create an API application at trakt.tv/oauth/applications, then enter its app details below. These are not your Trakt password.", style = MaterialTheme.typography.bodyLarge) }
                item { TraktField("Client ID", clientId, { clientId = it }) }
                item { TraktField("Client secret (if required by your app)", secret, { secret = it }, password = true) }
                item {
                    Button(onClick = { model.saveClient(clientId, secret); secret = "" }, enabled = !busy && clientId.isNotBlank()) { Text("Save Trakt app") }
                }
            }
            if (state.clientConfigured && !state.authorized && state.pairing == null) {
                item { Button(onClick = model::connect, enabled = !busy && !state.loading) { Text("Connect Trakt") } }
            }
            state.pairing?.let { pairing ->
                item { Text("On your phone or computer, visit", style = MaterialTheme.typography.bodyLarge) }
                item { Text(pairing.verificationUrl, style = MaterialTheme.typography.titleLarge) }
                item { Text(pairing.userCode, style = MaterialTheme.typography.headlineLarge) }
                item { Text("Enter this code and approve the connection. Keep this screen open while you sign in.", style = MaterialTheme.typography.bodyLarge) }
                item { Button(onClick = model::cancelSignIn) { Text("Cancel sign-in") } }
            }
            item { Text(message ?: state.message ?: if (busy || state.loading) "Working…" else if (state.authorized) "Your list is ready." else if (state.clientConfigured) "Trakt app saved. Connect your account above." else "Trakt is not connected.", style = MaterialTheme.typography.bodyLarge) }
            if (state.clientConfigured && state.pairing == null) {
                item { Button(onClick = model::disconnect, enabled = !busy && !state.loading) { Text("Disconnect and remove saved app details") } }
            }
            item { Text("Saved app details and sign-in tokens are encrypted on this TV. Long-press a title in Movies or Shows to remove it from the selected Trakt list. Watch history is not changed.", style = MaterialTheme.typography.bodyMedium) }
            item { Text("List names and title details are cached on this TV. Vista does not send them to a Vista server. Disconnecting removes the local saved list.", style = MaterialTheme.typography.bodyMedium) }
            item { Text("Vista is not endorsed or certified by Trakt.", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun TraktField(label: String, value: String, onChange: (String) -> Unit, password: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        BasicTextField(value = value, onValueChange = onChange, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Ascii, autoCorrectEnabled = false),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)).padding(15.dp))
    }
}
