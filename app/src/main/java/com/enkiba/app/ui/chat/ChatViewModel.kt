package com.enkiba.app.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enkiba.app.data.repository.ChatMessage
import com.enkiba.app.data.repository.ChatRepository
import com.enkiba.app.utils.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ChatUiState(
    val loading: Boolean = true,
    val isAdmin: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    /** Message dont on propose le retrait (appui long). */
    val pendingDelete: ChatMessage? = null,
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application)
    private val _state = MutableStateFlow(ChatUiState(isAdmin = SessionManager(application).isAdmin))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    private var polling: Job? = null

    /** Écran visible : rafraîchit tout de suite puis toutes les [POLL_MS]. */
    fun startPolling() {
        if (polling?.isActive == true) return
        polling = viewModelScope.launch {
            reload()
            while (isActive) {
                repository.refresh()
                reload()
                delay(POLL_MS)
            }
        }
    }

    fun stopPolling() {
        polling?.cancel()
        polling = null
    }

    private suspend fun reload() {
        val messages = repository.messages()
        repository.markAllRead(messages.lastOrNull()?.createdAt)
        _state.update { it.copy(loading = false, messages = messages) }
    }

    fun onDraft(text: String) = _state.update { it.copy(draft = text.take(ChatRepository.MAX_LENGTH)) }

    fun send() {
        val text = _state.value.draft
        if (text.isBlank()) return
        _state.update { it.copy(draft = "") }
        viewModelScope.launch {
            repository.send(text)
            reload()
            repository.refresh()
            reload()
        }
    }

    fun askDelete(message: ChatMessage) {
        if (message.mine || _state.value.isAdmin) _state.update { it.copy(pendingDelete = message) }
    }

    fun cancelDelete() = _state.update { it.copy(pendingDelete = null) }

    fun confirmDelete() {
        val m = _state.value.pendingDelete ?: return
        _state.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            repository.delete(m.id)
            reload()
            repository.refresh()
        }
    }

    private companion object {
        const val POLL_MS = 5_000L
    }
}
