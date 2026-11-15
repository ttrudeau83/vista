package dev.drewett.vista.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.drewett.vista.data.immich.ImmichRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File

data class ImmichBackgroundState(val file: File? = null, val fit: Boolean = false)

/** Photo work is independent of app discovery and never triggered by card focus. */
class ImmichBackgroundViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = ImmichRepository.get(app)
    private val _photo = MutableStateFlow(ImmichBackgroundState())
    val photo = _photo.asStateFlow()

    init {
        viewModelScope.launch {
            var activeNamespace: String? = null
            combine(repository.config, repository.photoRevision) { config, revision -> config to revision }
                .collectLatest { (config, _) ->
                    _photo.value = if (activeNamespace == config.namespace && config.configured)
                        _photo.value.copy(fit = config.fit) else ImmichBackgroundState(fit = config.fit)
                    activeNamespace = config.namespace
                    if (config.loading || !config.configured || config.albumId == null) return@collectLatest
                    try {
                        val file = repository.photo()
                        _photo.value = ImmichBackgroundState(file, config.fit)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Keep upstream artwork usable when the photo integration is unavailable.
                    }
                }
        }
    }
}
