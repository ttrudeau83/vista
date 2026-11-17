package dev.drewett.vista.ui.trakt

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.drewett.vista.data.trakt.TraktRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TraktSettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = TraktRepository.get(app)
    val state = repository.state
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private var actionJob: Job? = null

    fun saveClient(id: String, secret: String) = action { repository.saveClient(id, secret) }
    fun connect() = action { repository.startSignIn() }
    fun refresh() = action { repository.refreshLibrary(force = true) }
    fun selectList(id: Long?) = action { repository.selectList(id) }
    fun disconnect() = action { repository.disconnect() }
    fun cancelSignIn() {
        actionJob?.cancel()
        repository.cancelSignIn()
        _busy.value = false
    }

    override fun onCleared() {
        repository.cancelSignIn()
        super.onCleared()
    }

    private fun action(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _message.value = null
        actionJob = viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _message.value = "Could not complete the request. Check your Trakt app details and connection." }
            finally { _busy.value = false }
        }
    }
}
