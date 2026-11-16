package dev.drewett.vista.ui.music

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.drewett.vista.data.music.MusicChannel
import dev.drewett.vista.data.music.MusicPlaylist
import dev.drewett.vista.data.music.MusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MusicViewModel(app:Application):AndroidViewModel(app) {
    private val repository=MusicRepository.get(app)
    val state=repository.state
    private val _busy=MutableStateFlow(false)
    val busy=_busy.asStateFlow()
    private val _message=MutableStateFlow<String?>(null)
    val message=_message.asStateFlow()
    fun saveClient(id:String,secret:String)=action { repository.saveClient(id,secret) }
    fun connect()=action { repository.startSignIn() }
    fun refresh()=action { repository.refreshLibrary() }
    fun channel(channel:MusicChannel)=action { repository.selectChannel(channel) }
    fun playlist(playlist:MusicPlaylist)=action { repository.selectPlaylist(playlist) }
    fun disconnect()=action { repository.disconnect() }
    private fun action(block:suspend ()->Unit) {
        if(_busy.value) return
        _busy.value=true; _message.value=null
        viewModelScope.launch {
            try { block() }
            catch(cancelled:CancellationException) { throw cancelled }
            catch(_:Exception) { _message.value="Could not complete the request. Check the OAuth TV client, YouTube API access and connection." }
            finally { _busy.value=false }
        }
    }
}
