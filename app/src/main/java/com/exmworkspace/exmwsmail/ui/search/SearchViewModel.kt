package com.exmworkspace.exmwsmail.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.exmworkspace.exmwsmail.data.local.entity.MessageEntity
import com.exmworkspace.exmwsmail.data.repository.AuthRepository
import com.exmworkspace.exmwsmail.data.repository.MailRepository
import com.exmworkspace.exmwsmail.ui.appContainer
import com.exmworkspace.exmwsmail.ui.describeFailure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    private val mailRepository: MailRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _serverSearching = MutableStateFlow(false)
    val serverSearching: StateFlow<Boolean> = _serverSearching.asStateFlow()

    private val _serverError = MutableStateFlow<String?>(null)
    val serverError: StateFlow<String?> = _serverError.asStateFlow()

    private val _lastServerCount = MutableStateFlow<Int?>(null)
    val lastServerCount: StateFlow<Int?> = _lastServerCount.asStateFlow()

    private val accountId = MutableStateFlow<Long?>(null)

    init {
        viewModelScope.launch {
            val email = authRepository.activeMailEmail() ?: return@launch
            accountId.value = mailRepository.ensureAccount(email)
        }
    }

    fun searchOnServer() {
        if (_serverSearching.value) return
        val q = _query.value.trim()
        if (q.length < 2) return
        val id = accountId.value ?: return
        viewModelScope.launch {
            _serverSearching.value = true
            _serverError.value = null
            try {
                val added = mailRepository.searchOnServer(id, q)
                _lastServerCount.value = added
            } catch (e: Exception) {
                _serverError.value = authRepository.describeFailure(e)
            } finally {
                _serverSearching.value = false
            }
        }
    }

    val results: StateFlow<List<MessageEntity>> = _query
        .debounce(180)
        .flatMapLatest { q ->
            val id = accountId.value
            val trimmed = q.trim()
            if (id == null || trimmed.length < 2) flowOf(emptyList())
            else mailRepository.searchMessages(id, trimmed)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                SearchViewModel(container.mailRepository, container.authRepository)
            }
        }
    }
}
