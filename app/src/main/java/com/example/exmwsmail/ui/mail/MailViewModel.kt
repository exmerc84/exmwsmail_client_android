package com.example.exmwsmail.ui.mail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.exmwsmail.data.local.entity.FolderEntity
import com.example.exmwsmail.data.local.entity.MessageEntity
import com.example.exmwsmail.data.mail.FolderKind
import com.example.exmwsmail.data.repository.AuthRepository
import com.example.exmwsmail.data.repository.MailRepository
import com.example.exmwsmail.ui.appContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.mail.AuthenticationFailedException

@OptIn(ExperimentalCoroutinesApi::class)
class MailViewModel(
    private val mailRepository: MailRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val accountId = MutableStateFlow<Long?>(null)
    private val selectedFolderId = MutableStateFlow<Long?>(null)
    private val foldersExhausted = MutableStateFlow<Set<Long>>(emptySet())

    private val _selectedCategory = MutableStateFlow(MailCategory.ALL)
    val selectedCategory: StateFlow<MailCategory> = _selectedCategory.asStateFlow()

    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    val displayName: StateFlow<String> = authRepository.displayName

    fun selectCategory(category: MailCategory) {
        _selectedCategory.value = category
    }

    private val _refreshing = MutableStateFlow(false)
    private val _loadingOlder = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val folders: StateFlow<List<FolderEntity>> = accountId
        .filterNotNull()
        .flatMapLatest { mailRepository.observeFolders(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedFolder: StateFlow<FolderEntity?> = combine(selectedFolderId, folders) { id, list ->
        list.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val messages: StateFlow<List<MessageEntity>> = selectedFolderId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else mailRepository.observeMessages(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    val loadingOlder: StateFlow<Boolean> = _loadingOlder.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()

    val endReached: StateFlow<Boolean> = combine(selectedFolderId, foldersExhausted) { id, set ->
        id != null && id in set
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var topSyncJob: Job? = null
    private var olderSyncJob: Job? = null

    init {
        viewModelScope.launch { bootstrap() }
        // Auto-select INBOX once folders are available, but don't override user selection.
        viewModelScope.launch {
            folders.filter { it.isNotEmpty() }.first().also { list ->
                if (selectedFolderId.value == null) {
                    val inbox = list.firstOrNull { it.kind == FolderKind.INBOX } ?: list.firstOrNull()
                    inbox?.let { selectFolder(it.id) }
                }
            }
        }
    }

    private suspend fun bootstrap() {
        try {
            val email = authRepository.currentCredentials()?.email ?: return
            _userEmail.value = email
            val id = mailRepository.ensureAccount(email)
            accountId.value = id
            mailRepository.syncFolders(id)
        } catch (e: AuthenticationFailedException) {
            authRepository.signOut()
        } catch (e: Exception) {
            _error.update { e.message ?: e::class.java.simpleName }
        }
    }

    fun selectFolder(folderId: Long) {
        if (selectedFolderId.value == folderId) return
        selectedFolderId.value = folderId
        _error.value = null
        triggerTopSync(folderId)
    }

    fun refresh() {
        val folderId = selectedFolderId.value ?: return
        triggerTopSync(folderId, isRefresh = true)
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            try {
                mailRepository.deleteMessage(messageId)
            } catch (e: AuthenticationFailedException) {
                authRepository.signOut()
            } catch (e: Exception) {
                _error.update { e.message ?: e::class.java.simpleName }
            }
        }
    }

    fun toggleRead(messageId: Long) {
        viewModelScope.launch {
            try {
                val msg = messages.value.find { it.id == messageId } ?: return@launch
                mailRepository.markRead(messageId, !msg.seen)
            } catch (e: AuthenticationFailedException) {
                authRepository.signOut()
            } catch (e: Exception) {
                _error.update { e.message ?: e::class.java.simpleName }
            }
        }
    }

    fun loadMore() {
        val folderId = selectedFolderId.value ?: return
        if (_loadingOlder.value) return
        if (folderId in foldersExhausted.value) return
        olderSyncJob?.cancel()
        olderSyncJob = viewModelScope.launch {
            _loadingOlder.value = true
            try {
                val folder = mailRepository.findFolder(folderId) ?: return@launch
                val added = mailRepository.syncOlder(folder, limit = 50)
                if (added == 0) {
                    foldersExhausted.update { it + folderId }
                }
            } catch (e: AuthenticationFailedException) {
                authRepository.signOut()
            } catch (e: Exception) {
                _error.update { e.message ?: e::class.java.simpleName }
            } finally {
                _loadingOlder.value = false
            }
        }
    }

    private fun triggerTopSync(folderId: Long, isRefresh: Boolean = false) {
        topSyncJob?.cancel()
        topSyncJob = viewModelScope.launch {
            if (isRefresh) _refreshing.value = true
            try {
                val folder = mailRepository.findFolder(folderId) ?: return@launch
                mailRepository.syncFolderTop(folder, limit = 50)
                foldersExhausted.update { it - folderId }
            } catch (e: AuthenticationFailedException) {
                authRepository.signOut()
            } catch (e: Exception) {
                _error.update { e.message ?: e::class.java.simpleName }
            } finally {
                if (isRefresh) _refreshing.value = false
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                MailViewModel(container.mailRepository, container.authRepository)
            }
        }
    }
}
