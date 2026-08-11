package com.exmworkspace.exmwsmail.ui.followups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.exmworkspace.exmwsmail.data.remote.dto.FollowupDto
import com.exmworkspace.exmwsmail.data.repository.AuthRepository
import com.exmworkspace.exmwsmail.data.repository.MailRepository
import com.exmworkspace.exmwsmail.ui.appContainer
import com.exmworkspace.exmwsmail.ui.describeFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The "remind me about this mail" list (§4.19). */
class FollowupsViewModel(
    private val mailRepository: MailRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _items = MutableStateFlow<List<FollowupDto>>(emptyList())
    val items: StateFlow<List<FollowupDto>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            try {
                _items.value = mailRepository.followups().items
                _error.value = null
            } catch (e: Exception) {
                _error.value = authRepository.describeFailure(e)
            } finally {
                _loading.value = false
            }
        }
    }

    fun markDone(item: FollowupDto) = act { mailRepository.followupDone(item.id) }

    fun delete(item: FollowupDto) = act { mailRepository.deleteFollowup(item.id) }

    fun reschedule(item: FollowupDto, dueAt: String) =
        act { mailRepository.updateFollowup(item.id, dueAt, null) }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                refresh()
            } catch (e: Exception) {
                _error.value = authRepository.describeFailure(e)
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                FollowupsViewModel(container.mailRepository, container.authRepository)
            }
        }
    }
}
