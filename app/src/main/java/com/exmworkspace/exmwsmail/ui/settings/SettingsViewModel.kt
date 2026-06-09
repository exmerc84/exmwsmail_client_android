package com.exmworkspace.exmwsmail.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.exmworkspace.exmwsmail.data.repository.AuthRepository
import com.exmworkspace.exmwsmail.ui.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val storedDisplayName: StateFlow<String> = authRepository.displayName

    private val _draftName = MutableStateFlow(authRepository.displayName.value)
    val draftName: StateFlow<String> = _draftName.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    fun setDraftName(value: String) {
        _draftName.value = value
    }

    fun saveDisplayName() {
        if (_saving.value) return
        val value = _draftName.value
        _saving.update { true }
        viewModelScope.launch {
            try {
                authRepository.updateDisplayName(value)
            } finally {
                _saving.update { false }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(appContainer().authRepository)
            }
        }
    }
}
