package dev.drewett.vista.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.drewett.vista.data.calendar.FamilyCalendarRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FamilyCalendarViewModel(app: Application) : AndroidViewModel(app) {
    val repository = FamilyCalendarRepository.get(app)
    val state = repository.state
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    fun connect(url: String) = action { repository.configure(url); "Family calendar connected." }
    fun refresh() = action { repository.refresh(true); "Calendar refresh checked." }
    fun disconnect() = action { repository.disconnect(); "Calendar disconnected and cached events removed." }
    private fun action(block: suspend () -> String) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try { _message.value = block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _message.value = "Could not read this calendar. Check its private HTTPS iCal link and connection." }
            finally { _busy.value = false }
        }
    }
}
