package dev.drewett.vista.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.drewett.vista.data.immich.ImmichAlbum
import dev.drewett.vista.data.immich.ImmichException
import dev.drewett.vista.data.immich.ImmichRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ImmichSettingsState(
    val albums: List<ImmichAlbum> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
)

class ImmichSettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = ImmichRepository.get(app)
    val config = repository.config
    private val _state = MutableStateFlow(ImmichSettingsState())
    val state = _state.asStateFlow()
    private var action: Job? = null

    init {
        viewModelScope.launch {
            if (repository.awaitReady().configured) loadAlbums()
        }
    }

    fun connect(server: String, key: String) = perform {
        val albums = repository.configure(server, key, allowHttp = false)
        _state.value = _state.value.copy(albums = albums)
        if (albums.isEmpty()) "Connected. No accessible albums were found." else "Connected. Choose a photo album below."
    }

    fun loadAlbums() = perform {
        val albums = repository.albums()
        _state.value = _state.value.copy(albums = albums)
        if (albums.isEmpty()) "No accessible albums were found." else "Albums refreshed."
    }

    fun select(album: ImmichAlbum) = perform {
        repository.selectAlbum(album, config.value.fit)
        // Verify an actual image and prime the offline cache before reporting success.
        repository.photo()
            ?: throw ImmichException("This album has no usable photos.")
        "Background ready. Return to For you to see it."
    }

    fun toggleFit() = perform {
        repository.setFit(!config.value.fit)
        if (config.value.fit) "Fit: the complete photo is visible." else "Crop: the photo fills the screen."
    }

    fun refreshPhoto() = perform {
        repository.photo(refresh = true) ?: throw ImmichException("Choose an album with photos first.")
        "Photo refreshed."
    }

    fun disconnect() = perform {
        repository.disconnect()
        _state.value = _state.value.copy(albums = emptyList())
        "Disconnected. Saved credentials and cached photos were removed."
    }

    private fun perform(block: suspend () -> String) {
        if (action?.isActive == true) return
        action = viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            try {
                val message = block()
                _state.value = _state.value.copy(message = message)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = _state.value.copy(message =
                    if (error is ImmichException) error.message else "Could not complete the request. Try again.")
            } finally {
                _state.value = _state.value.copy(busy = false)
            }
        }
    }
}
