package com.exmworkspace.exmwsmail.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.exmworkspace.exmwsmail.data.remote.dto.ContactDto
import com.exmworkspace.exmwsmail.data.remote.dto.ContactCountsDto
import com.exmworkspace.exmwsmail.data.remote.dto.ContactGroupDto
import com.exmworkspace.exmwsmail.data.repository.AuthRepository
import com.exmworkspace.exmwsmail.data.repository.ContactsRepository
import com.exmworkspace.exmwsmail.ui.appContainer
import com.exmworkspace.exmwsmail.ui.describeFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ContactsTab { PERSONAL, CORPORATE }

class ContactsViewModel(
    private val contactsRepository: ContactsRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val personal = MutableStateFlow<List<ContactDto>>(emptyList())
    private val corporate = MutableStateFlow<List<ContactDto>>(emptyList())

    private val _groups = MutableStateFlow<List<ContactGroupDto>>(emptyList())
    val groups: StateFlow<List<ContactGroupDto>> = _groups.asStateFlow()

    /** Totals from `/api/contacts/count` (§5), shown under the title. */
    private val _counts = MutableStateFlow<ContactCountsDto?>(null)
    val counts: StateFlow<ContactCountsDto?> = _counts.asStateFlow()

    /** null = every group. */
    private val _selectedGroup = MutableStateFlow<String?>(null)
    val selectedGroup: StateFlow<String?> = _selectedGroup.asStateFlow()

    private val _tab = MutableStateFlow(ContactsTab.PERSONAL)
    val tab: StateFlow<ContactsTab> = _tab.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    /** Filtering is local: the lists are small and it keeps typing instant. */
    val contacts: StateFlow<List<ContactDto>> =
        combine(personal, corporate, _tab, _query, _selectedGroup) { own, dir, tab, q, group ->
            val source = if (tab == ContactsTab.PERSONAL) own else dir
            val needle = q.trim().lowercase()
            val byGroup = if (group == null) source else source.filter { it.groupName == group }
            val filtered = if (needle.isEmpty()) byGroup else byGroup.filter {
                it.displayLabel.lowercase().contains(needle) ||
                    it.email?.lowercase()?.contains(needle) == true ||
                    it.company?.lowercase()?.contains(needle) == true
            }
            filtered.sortedWith(
                compareByDescending<ContactDto> { it.isFavorite }
                    .thenBy { it.displayLabel.lowercase() }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val own = contactsRepository.list()
                personal.value = own
                corporate.value = runCatching { contactsRepository.corporate() }
                    .getOrDefault(emptyList())
                _groups.value = contactsRepository.groups()
                // Best-effort: the counters are a subtitle, not the screen.
                _counts.value = contactsRepository.counts()
            } catch (e: Exception) {
                _error.update { authRepository.describeFailure(e) }
            } finally {
                _loading.value = false
            }
        }
    }

    fun selectTab(value: ContactsTab) {
        _tab.value = value
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun selectGroup(name: String?) {
        _selectedGroup.value = name
    }

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun dismissNotice() {
        _notice.value = null
    }

    /** Groups live on the server; failures surface as-is instead of being worked around. */
    fun saveGroup(existing: ContactGroupDto?, name: String, domain: String, color: String) {
        viewModelScope.launch {
            try {
                contactsRepository.saveGroup(existing?.id, name, domain, color)
                refresh()
            } catch (e: Exception) {
                _notice.value = authRepository.describeFailure(e)
            }
        }
    }

    fun deleteGroup(group: ContactGroupDto) {
        val id = group.id ?: return
        viewModelScope.launch {
            try {
                contactsRepository.deleteGroup(id)
                if (_selectedGroup.value.equals(group.name, ignoreCase = true)) {
                    _selectedGroup.value = null
                }
                refresh()
            } catch (e: Exception) {
                _notice.value = authRepository.describeFailure(e)
            }
        }
    }

    fun toggleFavorite(contact: ContactDto) {
        val id = contact.id ?: return
        viewModelScope.launch {
            try {
                contactsRepository.toggleFavorite(id)
                refresh()
            } catch (e: Exception) {
                _error.update { authRepository.describeFailure(e) }
            }
        }
    }

    fun delete(contact: ContactDto) {
        val id = contact.id ?: return
        viewModelScope.launch {
            try {
                contactsRepository.delete(id)
                refresh()
            } catch (e: Exception) {
                _error.update { authRepository.describeFailure(e) }
            }
        }
    }

    /** Mines the mailbox for people already corresponded with (§5). */
    fun importFromEmails() {
        if (_importing.value) return
        viewModelScope.launch {
            _importing.value = true
            try {
                contactsRepository.importFromEmails()
                refresh()
            } catch (e: Exception) {
                _error.update { authRepository.describeFailure(e) }
            } finally {
                _importing.value = false
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                ContactsViewModel(container.contactsRepository, container.authRepository)
            }
        }
    }
}
