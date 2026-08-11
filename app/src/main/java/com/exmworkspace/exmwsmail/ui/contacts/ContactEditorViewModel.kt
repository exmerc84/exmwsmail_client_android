package com.exmworkspace.exmwsmail.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.exmworkspace.exmwsmail.data.remote.dto.ContactDto
import com.exmworkspace.exmwsmail.data.remote.dto.ContactGroupDto
import com.exmworkspace.exmwsmail.data.remote.dto.ContactUpsertDto
import com.exmworkspace.exmwsmail.data.repository.AuthRepository
import com.exmworkspace.exmwsmail.data.repository.ContactsRepository
import com.exmworkspace.exmwsmail.ui.appContainer
import com.exmworkspace.exmwsmail.ui.describeFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ContactFormState(
    val email: String = "",
    val displayName: String = "",
    val phone: String = "",
    val mobile: String = "",
    val company: String = "",
    val jobTitle: String = "",
    val department: String = "",
    val website: String = "",
    val address: String = "",
    val notes: String = "",
    val groupName: String = "",
    val isFavorite: Boolean = false,
    val source: String? = null,
) {
    val canSave: Boolean get() = email.isNotBlank() && '@' in email
}

/** Backs both "new contact" and "edit contact" — the only difference is whether id is set. */
class ContactEditorViewModel(
    private val contactId: Long?,
    private val contactsRepository: ContactsRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val isNew: Boolean = contactId == null

    private val _form = MutableStateFlow(ContactFormState())
    val form: StateFlow<ContactFormState> = _form.asStateFlow()

    private val _groups = MutableStateFlow<List<ContactGroupDto>>(emptyList())
    val groups: StateFlow<List<ContactGroupDto>> = _groups.asStateFlow()

    private val _loading = MutableStateFlow(contactId != null)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            _groups.value = contactsRepository.groups()
        }
        if (contactId != null) {
            viewModelScope.launch {
                try {
                    _form.value = contactsRepository.detail(contactId).toForm()
                } catch (e: Exception) {
                    _error.update { authRepository.describeFailure(e) }
                } finally {
                    _loading.value = false
                }
            }
        }
    }

    fun update(block: (ContactFormState) -> ContactFormState) {
        _form.update { block(it).also { _ -> _error.value = null } }
    }

    fun save() {
        val form = _form.value
        if (!form.canSave || _saving.value) return
        viewModelScope.launch {
            _saving.value = true
            try {
                val payload = form.toUpsert()
                if (contactId == null) {
                    contactsRepository.create(payload)
                } else {
                    contactsRepository.update(contactId, payload)
                }
                _saved.value = true
            } catch (e: Exception) {
                _error.update { authRepository.describeFailure(e) }
            } finally {
                _saving.value = false
            }
        }
    }

    /** Deleting closes the editor through the same [saved] signal the save path uses. */
    fun delete() {
        val id = contactId ?: return
        if (_saving.value) return
        viewModelScope.launch {
            _saving.value = true
            try {
                contactsRepository.delete(id)
                _saved.value = true
            } catch (e: Exception) {
                _error.update { authRepository.describeFailure(e) }
            } finally {
                _saving.value = false
            }
        }
    }

    companion object {
        fun factoryFor(contactId: Long?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                ContactEditorViewModel(
                    contactId,
                    container.contactsRepository,
                    container.authRepository,
                )
            }
        }
    }
}

private fun ContactDto.toForm() = ContactFormState(
    email = email.orEmpty(),
    displayName = displayLabel.takeIf { it != email } ?: displayName.orEmpty(),
    phone = phone.orEmpty(),
    mobile = mobile.orEmpty(),
    company = company.orEmpty(),
    jobTitle = jobTitle.orEmpty(),
    department = department.orEmpty(),
    website = website.orEmpty(),
    address = address.orEmpty(),
    notes = notes.orEmpty(),
    groupName = groupName.orEmpty(),
    isFavorite = isFavorite,
    source = source,
)

/**
 * The whole form travels, blanks included — that is what lets a field be cleared. See
 * [ContactUpsertDto] for why nulls would not work.
 */
private fun ContactFormState.toUpsert() = ContactUpsertDto(
    email = email.trim(),
    displayName = displayName.trim(),
    phone = phone.trim(),
    mobile = mobile.trim(),
    company = company.trim(),
    jobTitle = jobTitle.trim(),
    department = department.trim(),
    website = website.trim(),
    address = address.trim(),
    notes = notes.trim(),
    groupName = groupName.trim(),
)
