package com.example.exmwsmail.ui.mail.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.exmwsmail.data.repository.AuthRepository
import com.example.exmwsmail.data.repository.MailRepository
import com.example.exmwsmail.data.repository.MessageDetail
import com.example.exmwsmail.ui.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.mail.AuthenticationFailedException

class MessageDetailViewModel(
    private val messageId: Long,
    private val mailRepository: MailRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val detail: StateFlow<MessageDetail> = mailRepository
        .observeMessageDetail(messageId)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            MessageDetail(null, null, emptyList()),
        )

    private val _loadingBody = MutableStateFlow(true)
    val loadingBody: StateFlow<Boolean> = _loadingBody.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { mailRepository.markRead(messageId) }
            try {
                mailRepository.ensureBodyDownloaded(messageId)
            } catch (e: AuthenticationFailedException) {
                authRepository.signOut()
            } catch (e: Exception) {
                _error.update { e.message ?: e::class.java.simpleName }
            } finally {
                _loadingBody.value = false
            }
        }
    }

    fun toggleFlag() {
        viewModelScope.launch {
            try {
                mailRepository.toggleFlag(messageId)
            } catch (e: AuthenticationFailedException) {
                authRepository.signOut()
            } catch (e: Exception) {
                _error.update { e.message ?: e::class.java.simpleName }
            }
        }
    }

    fun markUnread() {
        viewModelScope.launch {
            try {
                mailRepository.markRead(messageId, seen = false)
            } catch (e: AuthenticationFailedException) {
                authRepository.signOut()
            } catch (e: Exception) {
                _error.update { e.message ?: e::class.java.simpleName }
            }
        }
    }

    fun deleteMessage() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                mailRepository.deleteMessage(messageId)
                _deleted.value = true
            } catch (e: AuthenticationFailedException) {
                authRepository.signOut()
            } catch (e: Exception) {
                _error.update { e.message ?: e::class.java.simpleName }
            } finally {
                _busy.value = false
            }
        }
    }

    companion object {
        fun factoryFor(messageId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                MessageDetailViewModel(messageId, container.mailRepository, container.authRepository)
            }
        }
    }
}
