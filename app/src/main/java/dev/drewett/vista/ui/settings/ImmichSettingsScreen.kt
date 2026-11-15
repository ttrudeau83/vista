package dev.drewett.vista.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun ImmichSettingsScreen(onDone: () -> Unit, model: ImmichSettingsViewModel = viewModel()) {
    val config by model.config.collectAsStateWithLifecycle()
    val state by model.state.collectAsStateWithLifecycle()
    var server by remember { mutableStateOf("") }
    // Deliberately not saveable: never put API keys into an Activity saved-state Bundle.
    var apiKey by remember { mutableStateOf("") }
    val doneFocus = remember { FocusRequester() }
    LaunchedEffect(config.loading) {
        if (!config.loading) server = config.serverUrl
    }
    LaunchedEffect(Unit) { doneFocus.requestFocus() }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Immich photo background", style = MaterialTheme.typography.headlineMedium)
                Button(onClick = { apiKey = ""; onDone() }, modifier = Modifier.focusRequester(doneFocus)) { Text("Done") }
            }
        }
        item { Text("Connect your server, then choose an album. This preview displays one photo on For you.", style = MaterialTheme.typography.bodyLarge) }
        item {
            SettingsField("Server URL", server, { server = it }, "https://photos.example.com", enabled = !state.busy && !config.loading)
        }
        item {
            SettingsField("API key", apiKey, { apiKey = it },
                if (config.configured) "Leave blank to keep the saved key for this server" else "Enter a dedicated Immich API key",
                password = true, enabled = !state.busy && !config.loading)
        }
        item {
            Text("Create a key in Immich user settings with album.read, asset.read and asset.view access. Some photos also need asset.download. Your key is stored encrypted on this device.", style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Button(onClick = {
                model.connect(server, apiKey)
                apiKey = ""
            }, enabled = !state.busy && !config.loading && server.isNotBlank()) { Text("Test connection and load albums") }
        }
        item {
            Text(if (state.busy) "Working…" else state.message ?: if (config.configured) "Connected. Select an album to enable photos." else "Not connected.",
                style = MaterialTheme.typography.bodyLarge)
        }
        if (config.configured) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Button(onClick = model::loadAlbums, enabled = !state.busy) { Text("Refresh albums") }
                    Button(onClick = model::toggleFit, enabled = !state.busy) { Text(if (config.fit) "Mode: Fit" else "Mode: Crop") }
                    Button(onClick = model::refreshPhoto, enabled = !state.busy && config.albumId != null) { Text("Refresh photo") }
                }
            }
            item { Text("Album: ${config.albumName ?: "None selected"}", style = MaterialTheme.typography.titleLarge) }
            items(state.albums, key = { it.id }) { album ->
                Button(onClick = { model.select(album) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text((if (album.id == config.albumId) "Selected · " else "") + album.name)
                }
            }
            item {
                Button(onClick = { apiKey = ""; model.disconnect() }, enabled = !state.busy) { Text("Disconnect and clear saved photos") }
            }
        }
    }
}

@Composable
private fun SettingsField(label: String, value: String, onChange: (String) -> Unit, hint: String, password: Boolean = false, enabled: Boolean) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Uri, autoCorrectEnabled = false),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
                .background(MaterialTheme.colorScheme.surface, shape)
                .border(if (focused) 3.dp else 1.dp, if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.border, shape)
                .padding(16.dp),
            decorationBox = { field ->
                Box {
                    if (value.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    field()
                }
            },
        )
    }
}
